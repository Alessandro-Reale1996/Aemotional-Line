package com.aemotionalline.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.view.AccountView;
import com.aemotionalline.application.view.TherapistView;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;
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
	private final Reauthentication reauthentication;
	private final Clock clock;

	UserService(UserRepository users, OneTimeLinkRepository links, CoupleRepository couples, CoupleInvitationRepository invitations,
			PasswordHasher hasher, Mailer mailer, AccountSessions sessions, CoupleService coupleService, Reauthentication reauthentication, Clock clock)
	{
		this.users = users;
		this.links = links;
		this.couples = couples;
		this.invitations = invitations;
		this.hasher = hasher;
		this.mailer = mailer;
		this.sessions = sessions;
		this.coupleService = coupleService;
		this.reauthentication = reauthentication;
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
			throw new DomainException(ErrorCode.EMAIL_ALREADY_USED, "An account with this email already exists.");
		}

		User user = User.pending(UserId.random(), email, displayName, role);
		users.save(user);
		mailer.sendActivationLink(email, user.getDisplayName(), issueLink(user.getId(), OneTimeLink.Purpose.ACTIVATION, ACTIVATION_VALIDITY));

		return user.getId();
	}

	/**
	 * The first administrator, created by a command on the server (CLAUDE.md, security point 6a and S4 decision 3): only
	 * while no active administrator exists. The code of the activation link is returned for the command to print on the
	 * terminal, never emailed. If the first administrator is still pending (its link lost or expired), running the
	 * command again with the same email gives a new link; any other email is refused.
	 */
	public String createFirstAdministrator(EmailAddress email, String displayName)
	{
		List<User> administrators = users.findByRole(Role.ADMINISTRATOR);

		if (administrators.stream().anyMatch(administrator -> administrator.getStatus() == AccountStatus.ACTIVE))
		{
			throw new DomainException(ErrorCode.ADMINISTRATOR_EXISTS, "An administrator already exists.");
		}

		Optional<User> pending = administrators.stream().filter(administrator -> administrator.getStatus() == AccountStatus.PENDING).findFirst();

		if (pending.isPresent())
		{
			if (!pending.get().getEmail().equals(email))
			{
				throw new DomainException(ErrorCode.ADMINISTRATOR_PENDING, "Another administrator account is waiting for activation.");
			}

			links.invalidateUnused(pending.get().getId(), OneTimeLink.Purpose.ACTIVATION, clock.instant());

			return issueLink(pending.get().getId(), OneTimeLink.Purpose.ACTIVATION, ACTIVATION_VALIDITY);
		}

		if (users.findByEmail(email).isPresent())
		{
			throw new DomainException(ErrorCode.EMAIL_ALREADY_USED, "An account with this email already exists.");
		}

		User administrator = User.pending(UserId.random(), email, displayName, Role.ADMINISTRATOR);
		users.save(administrator);

		return issueLink(administrator.getId(), OneTimeLink.Purpose.ACTIVATION, ACTIVATION_VALIDITY);
	}

	/** The therapists' accounts, for the administrator who creates and disables them. */
	@ReadOnlyTransaction
	public List<TherapistView> therapists(UserId administrator)
	{
		ensureActiveWithRole(administrator, Role.ADMINISTRATOR);

		return users.findByRole(Role.THERAPIST).stream()
				.map(therapist -> new TherapistView(therapist.getId(), therapist.getEmail().value(), therapist.getDisplayName(), therapist.getStatus()))
				.toList();
	}

	/** The user's own account, for the app's "who am I". */
	@ReadOnlyTransaction
	public AccountView account(UserId user)
	{
		return users.findById(user).map(found -> new AccountView(found.getId(), found.getEmail().value(), found.getDisplayName(), found.getRole()))
				.orElseThrow(() -> new NotFoundException("Not found."));
	}

	/** The owner opens the activation link and chooses the password. */
	public void activate(String code, String password)
	{
		OneTimeLink link = usableLinkOfLockedAccount(code, OneTimeLink.Purpose.ACTIVATION);
		User user = users.findById(link.user()).orElseThrow(UserService::invalidLink);

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
		OneTimeLink link = usableLinkOfLockedAccount(code, OneTimeLink.Purpose.PASSWORD_RESET);
		User user = users.findById(link.user()).orElseThrow(UserService::invalidLink);

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
	@Transactional(noRollbackFor = IncorrectPasswordException.class)
	public void disableTherapist(UserId administrator, UserId therapist, String password)
	{
		// The administrator confirms with their password (irreversible: REST point 5); then the therapist's account is
		// locked, so an invitation the therapist is sending (which locks the same account) is either already visible
		// here, or refused once this commits.
		if (reauthentication.confirm(administrator, password).getRole() != Role.ADMINISTRATOR)
		{
			throw new NotFoundException("Not found.");
		}

		User user = users.findByIdForUpdate(therapist).filter(found -> found.getRole() == Role.THERAPIST)
				.orElseThrow(() -> new NotFoundException("Therapist not found."));

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
	@Transactional(noRollbackFor = IncorrectPasswordException.class)
	public void deleteOwnPatientAccount(UserId patient, String password)
	{
		// The password confirms (irreversible: REST point 5) and locks the account: an acceptance of an invitation by
		// this patient (which locks the account too) can't overlap.
		User user = reauthentication.confirm(patient, password);

		if (user.getRole() != Role.PATIENT)
		{
			throw new NotFoundException("Not found.");
		}

		if (couples.findActiveCoupleOf(patient).isPresent())
		{
			throw new DomainException(ErrorCode.ACCOUNT_IN_ACTIVE_COUPLE, "An account in an active couple can't be deleted.");
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

	// The link names the account; once the account is locked the link is read again: of two uses of the same link at the
	// same moment, the second finds it used.
	private OneTimeLink usableLinkOfLockedAccount(String code, OneTimeLink.Purpose purpose)
	{
		users.findByIdForUpdate(usableLink(code, purpose).user()).orElseThrow(UserService::invalidLink);

		return usableLink(code, purpose);
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
		return new InvalidRequestException(RequestErrorCode.INVALID_LINK, "The link is not valid or has expired.");
	}
}
