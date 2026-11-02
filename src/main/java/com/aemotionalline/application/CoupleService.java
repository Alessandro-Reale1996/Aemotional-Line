package com.aemotionalline.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.CoupleDeletion.Cause;
import com.aemotionalline.domain.common.DomainException;
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
	private final PasswordHasher hasher;
	private final Clock clock;

	CoupleService(CoupleRepository couples, ConversationRepository conversations, NegotiationRepository negotiations, UserRepository users,
			CoupleErasure erasure, PasswordHasher hasher, Clock clock)
	{
		this.couples = couples;
		this.conversations = conversations;
		this.negotiations = negotiations;
		this.users = users;
		this.erasure = erasure;
		this.hasher = hasher;
		this.clock = clock;
	}

	/** The therapist ends the couple; a partner is refused by the domain, an outsider is told the couple doesn't exist. */
	public void end(UserId therapist, CoupleId id)
	{
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
		User user = reauthenticated(actor, password);
		Couple couple = couples.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Couple not found."));
		Cause cause = deletionCause(user, couple);

		if (couple.isActive())
		{
			throw new DomainException("The couple must be ended before its data can be deleted.");
		}

		CoupleLocks.lockContentOf(conversations, negotiations, id);
		erasure.erase(new CoupleDeletion(id, couple.getTherapistId(), couple.getEndedAt().orElseThrow(), clock.instant(), cause, Optional.of(actor)));
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

	// The account's own password confirms the operation; the account is locked for this, like a login.
	private User reauthenticated(UserId actor, String password)
	{
		User user = users.findByIdForUpdate(actor).filter(found -> found.getStatus() == AccountStatus.ACTIVE)
				.orElseThrow(() -> new NotFoundException("Not found."));
		Instant now = clock.instant();

		if (!user.canLogInAt(now))
		{
			throw new IncorrectPasswordException();
		}

		if (!hasher.matches(password, user.getPasswordHash().orElseThrow()))
		{
			user.recordFailedLogin(now);
			users.save(user);
			throw new IncorrectPasswordException();
		}

		if (user.getFailedLogins() > 0)
		{
			user.recordSuccessfulLogin();
			users.save(user);
		}

		return user;
	}
}
