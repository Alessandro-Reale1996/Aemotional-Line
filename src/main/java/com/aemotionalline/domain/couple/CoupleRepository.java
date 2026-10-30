package com.aemotionalline.domain.couple;

import java.util.Optional;

/** Where couples are kept. Plain Java: the persistence layer implements it, the domain doesn't know how. */
public interface CoupleRepository
{
	Optional<Couple> findById(CoupleId id);

	void save(Couple couple);
}
