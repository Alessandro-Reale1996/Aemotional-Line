package com.aemotionalline.infrastructure.persistence.couple;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;

import org.springframework.data.repository.ListCrudRepository;

interface CoupleRowRepository extends ListCrudRepository<CoupleRow, UUID>
{
	// SELECT ... FOR UPDATE: the row stays locked until the end of the transaction.
	@Lock(LockMode.PESSIMISTIC_WRITE)
	Optional<CoupleRow> findLockedById(UUID id);
}
