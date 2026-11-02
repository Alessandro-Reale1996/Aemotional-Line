package com.aemotionalline.domain.couple;

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

	void save(Couple couple);
}
