package com.aemotionalline.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.CoupleDeletion.Cause;
import com.aemotionalline.application.view.EndedCoupleView;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.negotiation.NegotiationRepository;
import com.aemotionalline.domain.user.AccountStatus;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * The end of a couple and the deletion of its data (CLAUDE.md, S3 part 2). The therapist ends the couple when the therapy
 * ends: its conversation becomes read-only and everything pending stays frozen. Once ended, the therapist can delete all
 * its data, confirming with their own password; when the therapist's account is disabled, an administrator can do it
 * instead, without ever being able to read the data. Only a minimal trace of a deleted couple stays.
 */
@Service
@Transactional
public class CoupleService
{
	private final CoupleRepository couples;
	private final ConversationRepository conversations;
	private final NegotiationRepository negotiations;
	private final UserRepository users;
	private final CoupleErasure erasure;
	private final Reauthentication reauthentication;
	private final Clock clock;

	CoupleService(CoupleRepository couples, ConversationRepository conversations, NegotiationRepository negotiations, UserRepository users,
			CoupleErasure erasure, Reauthentication reauthentication, Clock clock)
	{
		this.couples = couples;
		this.conversations = conversations;
		this.negotiations = negotiations;
		this.users = users;
		this.erasure = erasure;
		this.reauthentication = reauthentication;
		this.clock = clock;
	}

	/**
	 * The therapist ends the couple, confirming with their password (irreversible: REST point 5); a partner is refused by
	 * the domain, an outsider is told the couple doesn't exist.
	 */
	@Transactional(noRollbackFor = IncorrectPasswordException.class)
	public void end(UserId therapist, CoupleId id, String password)
	{
		reauthentication.confirm(therapist, password);
		Couple couple = couples.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Couple not found."));
		Access.ensureMember(couple, therapist, "Couple");
		CoupleLocks.lockContentOf(conversations, negotiations, id);

		couple.end(therapist, clock.instant());
		couples.save(couple);
	}

	/** Disabling a therapist ends every active couple of theirs; the caller already holds the therapist's account lock. */
	void endEveryActiveCoupleOf(UserId therapist)
	{
		for (Couple couple : couples.findActiveCouplesOfTherapistForUpdate(therapist))
		{
			CoupleLocks.lockContentOf(conversations, negotiations, couple.getId());
			couple.end(therapist, clock.instant());
			couples.save(couple);
		}
	}

	/**
	 * Deletes every piece of data of an ended couple, irreversibly. The password is checked first, and a wrong one counts
	 * as a failed login (so a stolen session can't be used to guess it): that failure is committed, nothing else changes.
	 */
	@Transactional(noRollbackFor = IncorrectPasswordException.class)
	public void deleteData(UserId actor, CoupleId id, String password)
	{
		User user = reauthentication.confirm(actor, password);
		Couple couple = couples.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Couple not found."));
		Cause cause = deletionCause(user, couple);

		if (couple.isActive())
		{
			throw new DomainException(ErrorCode.COUPLE_NOT_ENDED, "The couple must be ended before its data can be deleted.");
		}

		CoupleLocks.lockContentOf(conversations, negotiations, id);
		erasure.erase(new CoupleDeletion(id, couple.getTherapistId(), couple.getEndedAt().orElseThrow(), clock.instant(), cause, Optional.of(actor)));
	}

	/**
	 * The ended couples of a disabled therapist, for the administrator who may delete their data (CLAUDE.md, R4): only
	 * their ids and end dates. For an active therapist there is nothing for the administrator to see: "not found".
	 */
	@ReadOnlyTransaction
	public List<EndedCoupleView> endedCouplesOfDisabledTherapist(UserId administrator, UserId therapist)
	{
		boolean isAdministrator = users.findById(administrator)
				.filter(user -> user.getRole() == Role.ADMINISTRATOR && user.getStatus() == AccountStatus.ACTIVE).isPresent();
		boolean isDisabledTherapist = users.findById(therapist)
				.filter(user -> user.getRole() == Role.THERAPIST && user.getStatus() == AccountStatus.DISABLED).isPresent();

		if (!isAdministrator || !isDisabledTherapist)
		{
			throw new NotFoundException("Not found.");
		}

		return couples.findCouplesOf(therapist).stream()
				.filter(couple -> couple.isTherapist(therapist) && !couple.isActive())
				.map(couple -> new EndedCoupleView(couple.getId(), couple.getEndedAt().orElseThrow()))
				.toList();
	}

	// The couple's therapist, or an administrator once the therapist's account is disabled; anyone else is told the couple
	// doesn't exist.
	private Cause deletionCause(User user, Couple couple)
	{
		if (user.getRole() == Role.THERAPIST && couple.isTherapist(user.getId()))
		{
			return Cause.THERAPIST;
		}

		if (user.getRole() == Role.ADMINISTRATOR
				&& users.findById(couple.getTherapistId()).filter(therapist -> therapist.getStatus() == AccountStatus.DISABLED).isPresent())
		{
			return Cause.ADMINISTRATOR;
		}

		throw new NotFoundException("Couple not found.");
	}
}
