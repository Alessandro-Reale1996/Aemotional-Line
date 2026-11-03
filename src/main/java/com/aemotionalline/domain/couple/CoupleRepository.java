package com.aemotionalline.domain.couple;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.user.UserId;

/** Where couples are kept. Plain Java: the persistence layer implements it, the domain doesn't know how. */
public interface CoupleRepository
{
	Optional<Couple> findById(CoupleId id);

	/**
	 * Like {@link #findById}, but locks the couple until the end of the transaction: starting a negotiation locks it, so
	 * two simultaneous starts queue up. Lock order: couple, then conversation, then negotiation.
	 */
	Optional<Couple> findByIdForUpdate(CoupleId id);

	/** The active couple a patient is a partner of, if any: a patient is in at most one active couple at a time. */
	Optional<Couple> findActiveCoupleOf(UserId patient);

	/**
	 * The active couples a therapist follows, locked until the end of the transaction (disabling the therapist ends them):
	 * a locking read, so it also sees a couple born from an invitation accepted a moment ago.
	 */
	List<Couple> findActiveCouplesOfTherapistForUpdate(UserId therapist);

	/** The user's couples, as a partner or as the therapist: the active ones first, then the ended ones, latest end first. */
	List<Couple> findCouplesOf(UserId user);

	/** The couples ended before the instant, oldest end first (the retention period deletes their data). */
	List<CoupleId> findIdsEndedBefore(Instant instant);

	void save(Couple couple);
}
