package com.aemotionalline.infrastructure.persistence.negotiation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.ListCrudRepository;

interface NegotiationRowRepository extends ListCrudRepository<NegotiationRow, UUID>
{
	// SELECT ... FOR UPDATE: the row stays locked until the end of the transaction.
	@Lock(LockMode.PESSIMISTIC_WRITE)
	Optional<NegotiationRow> findLockedById(UUID id);

	List<NegotiationRow> findByCoupleIdAndStatusInOrderByStartedAt(UUID coupleId, Collection<String> statuses);
}
