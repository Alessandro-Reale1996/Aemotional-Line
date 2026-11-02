package com.aemotionalline.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.CoupleInvitation;
import com.aemotionalline.domain.couple.CoupleInvitationRepository;
import com.aemotionalline.domain.couple.CoupleInvitationStatus;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.AccountStatus;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * Accounts: the administrator creates and disables therapists; every account is activated through a one-time link sent to
 * its email (which also verifies it); a forgotten password is reset through another one-time link; a patient can delete
 * their own account when not in an active couple. The answer to a reset request never reveals whether an email exists.
 */
@Service
@Transactional
public class UserService
{
	static final Duration ACTIVATION_VALIDITY = Duration.ofDays(7);
	static final Duration RESET_VALIDITY = Duration.ofHours(1);

	private final UserRepository users;
	private final OneTimeLinkRepository links;
	private final CoupleRepository couples;
	private final CoupleInvitationRepository invitations;
	private final PasswordHasher hasher;
	private final Mailer mailer;
	private final AccountSessions sessions;
	private final CoupleService coupleService;
	private final Clock clock;

	UserService(UserRepository users, OneTimeLinkRepository links, CoupleRepository couples, CoupleInvitationRepository invitations,
			PasswordHasher hasher, Mailer mailer, AccountSessions sessions, CoupleService coupleService, Clock clock)
	{
		this.users = users;
		this.links = links;
		this.couples = couples;
		this.invitations = invitations;
		this.hasher = hasher;
		this.mailer = mailer;
		this.sessions = sessions;
		this.coupleService = coupleService;
		this.clock = clock;
	}

	/** The administrator creates a therapist's account; the therapist receives the activation link. */
	public UserId createTherapist(UserId administrator, EmailAddress email, String displayName)
	{
		ensureActiveWithRole(administrator, Role.ADMINISTRATOR);

		return createPending(email, displayName, Role.THERAPIST);
	}

	/** A new pending account and its activation email. Used for therapists here and for patients by the invitations. */
	UserId createPending(EmailAddress email, String displayName, Role role)
	{
		if (users.findByEmail(email).isPresent())
		{
			throw new DomainException("An account with this email already exists.");
		}

		User user = User.pending(UserId.random(), email, displayName, role);
		users.save(user);
		mailer.sendActivationLink(email, user.getDisplayName(), issueLink(user.getId(), OneTimeLink.Purpose.ACTIVATION, ACTIVATION_VALIDITY));

		return user.getId();
	}

	/** The owner opens the activation link and chooses the password. */
	public void activate(String code, String password)
	{
		OneTimeLink link = usableLink(code, OneTimeLink.Purpose.ACTIVATION);
		User user = users.findByIdForUpdate(link.user()).orElseThrow(() -> invalidLink());

		PasswordPolicy.check(password, user.getEmail());
		user.activate(hasher.hash(password));
		users.save(user);
		links.save(link.usedAt(clock.instant()));
	}

	/** Always answers the same way: whether the email exists is never revealed. Only an active account gets an email. */
	public void requestPasswordReset(EmailAddress email)
	{
		Optional<User> user = users.findByEmail(email).filter(found -> found.getStatus() == AccountStatus.ACTIVE);

		user.ifPresent(found ->
		{
			links.invalidateUnused(found.getId(), OneTimeLink.Purpose.PASSWORD_RESET, clock.instant());
			mailer.sendPasswordResetLink(found.getEmail(), found.getDisplayName(),
					issueLink(found.getId(), OneTimeLink.Purpose.PASSWORD_RESET, RESET_VALIDITY));
		});
	}

	/** The owner opens the reset link and chooses a new password; every open session of the account ends. */
	public void resetPassword(String code, String password)
	{
		OneTimeLink link = usableLink(code, OneTimeLink.Purpose.PASSWORD_RESET);
		User user = users.findByIdForUpdate(link.user()).orElseThrow(() -> invalidLink());

		PasswordPolicy.check(password, user.getEmail());
		user.changePassword(hasher.hash(password));
		users.save(user);
		links.save(link.usedAt(clock.instant()));
		sessions.endAllOf(user.getId());
	}

	/**
	 * The administrator disables a therapist's account: its open sessions end at once, its pending invitations are
	 * withdrawn and its active couples end (frozen like any end; an administrator can then delete their data).
	 */
	public void disableTherapist(UserId administrator, UserId therapist)
	{
		// The account is locked before anything is read: an invitation the therapist is sending (which locks the same
		// account) is either already visible here, or refused once this commits.
		Optional<User> locked = users.findByIdForUpdate(therapist);
		ensureActiveWithRole(administrator, Role.ADMINISTRATOR);
		User user = locked.filter(found -> found.getRole() == Role.THERAPIST).orElseThrow(() -> new NotFoundException("Therapist not found."));

		user.disable();
		users.save(user);
		withdrawPendingInvitationsOf(therapist);
		coupleService.endEveryActiveCoupleOf(therapist);
		sessions.endAllOf(therapist);
	}

	/**
	 * A patient deletes their own account, only when not in an active couple (the messages of an ended couple belong to
	 * both partners and follow the couple's fate). Their pending invitations are declined and their links removed.
	 */
	public void deleteOwnPatientAccount(UserId patient)
	{
		User user = ensureActiveWithRole(patient, Role.PATIENT);

		if (couples.findActiveCoupleOf(patient).isPresent())
		{
			throw new DomainException("An account in an active couple can't be deleted.");
		}

		Instant now = clock.instant();

		invitations.findPendingInvolving(patient).forEach(invitation ->
		{
			invitation.decline(patient, now);
			invitations.save(invitation);
		});

		sessions.endAllOf(patient);
		users.delete(user.getId());
	}

	User ensureActiveWithRole(UserId user, Role role)
	{
		return users.findById(user)
				.filter(found -> found.getRole() == role && found.getStatus() == AccountStatus.ACTIVE)
				.orElseThrow(() -> new NotFoundException("Not found."));
	}

	// Expired invitations are left alone: they can't be accepted anyway.
	private void withdrawPendingInvitationsOf(UserId therapist)
	{
		Instant now = clock.instant();

		for (CoupleInvitation pending : invitations.findPendingInvolving(therapist))
		{
			invitations.findByIdForUpdate(pending.getId())
					.filter(invitation -> invitation.getTherapistId().equals(therapist) && invitation.getStatus() == CoupleInvitationStatus.PENDING
							&& now.isBefore(invitation.getExpiresAt()))
					.ifPresent(invitation ->
					{
						invitation.withdraw(therapist, now);
						invitations.save(invitation);
					});
		}
	}

	private String issueLink(UserId user, OneTimeLink.Purpose purpose, Duration validity)
	{
		String code = OneTimeCodes.newCode();
		Instant now = clock.instant();
		links.save(new OneTimeLink(OneTimeCodes.hash(code), user, purpose, now, now.plus(validity), Optional.empty()));

		return code;
	}

	private OneTimeLink usableLink(String code, OneTimeLink.Purpose purpose)
	{
		if (code == null || code.isBlank())
		{
			throw invalidLink();
		}

		return links.findByCodeHash(OneTimeCodes.hash(code))
				.filter(link -> link.purpose() == purpose && link.isUsableAt(clock.instant()))
				.orElseThrow(UserService::invalidLink);
	}

	// One message for every kind of bad link: unknown, used, expired or of another purpose.
	private static InvalidRequestException invalidLink()
	{
		return new InvalidRequestException("The link is not valid or has expired.");
	}
}
