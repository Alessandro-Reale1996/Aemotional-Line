package com.aemotionalline.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.view.InvitationView;
import com.aemotionalline.domain.common.DomainException;
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
			throw new DomainException("The two partners need two different emails.");
		}

		UserId one = patientFor(emailOne, nameOne);
		UserId two = patientFor(emailTwo, nameTwo);
		Instant now = clock.instant();

		CoupleInvitation invitation = CoupleInvitation.create(CoupleInvitationId.random(), therapist, one, two, now, now.plus(VALIDITY));
		invitations.save(invitation);

		for (UserId partner : List.of(one, two))
		{
			User user = users.findById(partner).orElseThrow();
			mailer.sendCoupleInvitation(user.getEmail(), user.getDisplayName(), inviter.getDisplayName());
		}

		return invitation.getId();
	}

	/** A partner accepts; the second acceptance creates the couple. Neither partner may already be in an active couple. */
	public void accept(UserId patient, CoupleInvitationId id)
	{
		userService.ensureActiveWithRole(patient, Role.PATIENT);
		// Serialises this patient's acceptances, so two invitations can't both make them a partner.
		users.findByIdForUpdate(patient).orElseThrow();

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
	@Transactional(readOnly = true)
	public List<InvitationView> pending(UserId user)
	{
		Instant now = clock.instant();

		return invitations.findPendingInvolving(user).stream()
				.filter(invitation -> now.isBefore(invitation.getExpiresAt()))
				.map(InvitationService::view)
				.toList();
	}

	@Transactional(readOnly = true)
	public InvitationView view(UserId user, CoupleInvitationId id)
	{
		return invitations.findById(id).filter(invitation -> involves(invitation, user)).map(InvitationService::view)
				.orElseThrow(() -> new NotFoundException("Invitation not found."));
	}

	// An existing patient (not disabled, not in an active couple) or a new pending patient account with its activation email.
	private UserId patientFor(EmailAddress email, String displayName)
	{
		Optional<User> existing = users.findByEmail(email);

		if (existing.isEmpty())
		{
			return userService.createPending(email, displayName, Role.PATIENT);
		}

		User user = existing.get();

		// One message whatever the reason, so the therapist learns nothing about other people's accounts or couples.
		if (user.getRole() != Role.PATIENT || user.getStatus() == AccountStatus.DISABLED || couples.findActiveCoupleOf(user.getId()).isPresent())
		{
			throw new DomainException("This person can't be invited now.");
		}

		return user.getId();
	}

	private void ensureNotInAnActiveCouple(UserId patient)
	{
		if (couples.findActiveCoupleOf(patient).isPresent())
		{
			throw new DomainException("A partner is already in an active couple.");
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

	private static InvitationView view(CoupleInvitation invitation)
	{
		return new InvitationView(invitation.getId(), invitation.getTherapistId(), invitation.getPartnerOneId(), invitation.getPartnerTwoId(),
				invitation.getStatus(), invitation.getAcceptedBy(), invitation.getCreatedAt(), invitation.getExpiresAt(), invitation.getCoupleId());
	}
}
