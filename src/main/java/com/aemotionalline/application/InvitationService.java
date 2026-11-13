package com.aemotionalline.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.view.InvitationView;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleInvitation;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.couple.CoupleInvitationRepository;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.AccountStatus;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * How a couple is born (spec 1.2): the therapist invites two patients by email (a patient without an account gets a pending
 * one and its activation link); the couple exists once both have accepted. A patient is in at most one active couple at a
 * time. Only the therapist and the two invited partners can see an invitation.
 */
@Service
@Transactional
public class InvitationService
{
	static final Duration VALIDITY = Duration.ofDays(7);

	private final UserService userService;
	private final UserRepository users;
	private final CoupleInvitationRepository invitations;
	private final CoupleRepository couples;
	private final Mailer mailer;
	private final Clock clock;

	InvitationService(UserService userService, UserRepository users, CoupleInvitationRepository invitations, CoupleRepository couples, Mailer mailer,
			Clock clock)
	{
		this.userService = userService;
		this.users = users;
		this.invitations = invitations;
		this.couples = couples;
		this.mailer = mailer;
		this.clock = clock;
	}

	public CoupleInvitationId invite(UserId therapist, EmailAddress emailOne, String nameOne, EmailAddress emailTwo, String nameTwo)
	{
		// Locks the therapist's account: an invitation and the disabling of the account (which withdraws the pending
		// invitations) can't overlap.
		User inviter = users.findByIdForUpdate(therapist).filter(found -> found.getRole() == Role.THERAPIST && found.getStatus() == AccountStatus.ACTIVE)
				.orElseThrow(() -> new NotFoundException("Not found."));

		if (emailOne.equals(emailTwo))
		{
			throw new DomainException(ErrorCode.SAME_EMAIL_TWICE, "The two partners need two different emails.");
		}

		Optional<User> knownOne = invitableAccount(emailOne);
		Optional<User> knownTwo = invitableAccount(emailTwo);
		User one = knownOne.orElseGet(() -> userService.createPendingAccount(emailOne, nameOne, Role.PATIENT));
		User two = knownTwo.orElseGet(() -> userService.createPendingAccount(emailTwo, nameTwo, Role.PATIENT));
		Instant now = clock.instant();

		CoupleInvitation invitation = CoupleInvitation.create(CoupleInvitationId.random(), therapist, one.getId(), two.getId(), now, now.plus(VALIDITY));
		invitations.save(invitation);

		// The emails last, once everything else has succeeded: an email can't be taken back if the invitation is refused
		// (CLAUDE.md, F5). Whoever's account is waiting for activation, a new one or one whose first link was lost or
		// expired, gets a new link, and the earlier ones stop working.
		for (User partner : List.of(one, two))
		{
			if (partner.getStatus() == AccountStatus.PENDING)
			{
				userService.renewActivationLink(partner);
			}

			mailer.sendCoupleInvitation(partner.getEmail(), partner.getDisplayName(), inviter.getDisplayName());
		}

		return invitation.getId();
	}

	/** A partner accepts; the second acceptance creates the couple. Neither partner may already be in an active couple. */
	public void accept(UserId patient, CoupleInvitationId id)
	{
		CoupleInvitation found = invitations.findById(id).filter(invitation -> involves(invitation, patient))
				.orElseThrow(() -> new NotFoundException("Invitation not found."));
		// Both partners' accounts are locked first, always in the same order: any acceptance involving either of them (in
		// this invitation or another) waits, so neither can end up in two active couples.
		lockAccounts(found.getPartnerOneId(), found.getPartnerTwoId());
		userService.ensureActiveWithRole(patient, Role.PATIENT);

		CoupleInvitation invitation = lockedFor(patient, id);
		ensureNotInAnActiveCouple(patient);

		Optional<Couple> couple = invitation.accept(patient, clock.instant());

		if (couple.isPresent())
		{
			UserId other = invitation.getPartnerOneId().equals(patient) ? invitation.getPartnerTwoId() : invitation.getPartnerOneId();
			ensureNotInAnActiveCouple(other);
			couples.save(couple.get());
		}

		invitations.save(invitation);
	}

	public void decline(UserId patient, CoupleInvitationId id)
	{
		CoupleInvitation invitation = lockedFor(patient, id);
		invitation.decline(patient, clock.instant());
		invitations.save(invitation);
	}

	public void withdraw(UserId therapist, CoupleInvitationId id)
	{
		CoupleInvitation invitation = lockedFor(therapist, id);
		invitation.withdraw(therapist, clock.instant());
		invitations.save(invitation);
	}

	/** The user's invitations still waiting for an answer (an expired one is no longer listed). */
	@ReadOnlyTransaction
	public List<InvitationView> pending(UserId user)
	{
		Instant now = clock.instant();

		return invitations.findPendingInvolving(user).stream()
				.filter(invitation -> now.isBefore(invitation.getExpiresAt()))
				.map(this::view)
				.toList();
	}

	@ReadOnlyTransaction
	public InvitationView view(UserId user, CoupleInvitationId id)
	{
		return invitations.findById(id).filter(invitation -> involves(invitation, user)).map(this::view)
				.orElseThrow(() -> new NotFoundException("Invitation not found."));
	}

	// An existing patient (not disabled, not in an active couple) or a new pending patient account with its activation email.
	/** The account of an invited person, if there is one, once it is known to be invitable; empty for a new person. */
	private Optional<User> invitableAccount(EmailAddress email)
	{
		Optional<User> existing = users.findByEmail(email);

		// One message whatever the reason, so the therapist learns nothing about other people's accounts or couples.
		if (existing.isPresent() && (existing.get().getRole() != Role.PATIENT || existing.get().getStatus() == AccountStatus.DISABLED
				|| couples.findActiveCoupleOf(existing.get().getId()).isPresent()))
		{
			throw new DomainException(ErrorCode.CANNOT_BE_INVITED, "This person can't be invited now.");
		}

		return existing;
	}

	private void lockAccounts(UserId one, UserId two)
	{
		List.of(one, two).stream().sorted(Comparator.comparing(UserId::value)).forEach(users::findByIdForUpdate);
	}

	private void ensureNotInAnActiveCouple(UserId patient)
	{
		if (couples.findActiveCoupleOf(patient).isPresent())
		{
			throw new DomainException(ErrorCode.PARTNER_IN_ACTIVE_COUPLE, "A partner is already in an active couple.");
		}
	}

	private CoupleInvitation lockedFor(UserId user, CoupleInvitationId id)
	{
		return invitations.findByIdForUpdate(id).filter(invitation -> involves(invitation, user))
				.orElseThrow(() -> new NotFoundException("Invitation not found."));
	}

	private static boolean involves(CoupleInvitation invitation, UserId user)
	{
		return invitation.getTherapistId().equals(user) || invitation.isInvited(user);
	}

	private InvitationView view(CoupleInvitation invitation)
	{
		return new InvitationView(invitation.getId(), People.of(users, invitation.getTherapistId()), People.of(users, invitation.getPartnerOneId()),
				People.of(users, invitation.getPartnerTwoId()), invitation.getStatus(), invitation.getAcceptedBy(), invitation.getCreatedAt(),
				invitation.getExpiresAt(), invitation.getCoupleId());
	}
}
