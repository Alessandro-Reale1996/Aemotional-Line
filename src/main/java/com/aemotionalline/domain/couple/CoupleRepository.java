package com.aemotionalline.domain.couple;

import java.util.Optional;

/** Where couples are kept. Plain Java: the persistence layer implements it, the domain doesn't know how. */
public interface CoupleRepository
{
	Optional<Couple> findById(CoupleId id);

	/**
	 * Like {@link #findById}, but locks the couple until the end of the transaction: starting a negotiation locks it, so
	 * two simultaneous starts queue up. Lock order: couple, then conversation, then negotiation.
	 */
	Optional<Couple> findByIdForUpdate(CoupleId id);

	void save(Couple couple);
}
