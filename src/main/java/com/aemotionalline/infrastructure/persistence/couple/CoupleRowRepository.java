package com.aemotionalline.infrastructure.persistence.couple;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

interface CoupleRowRepository extends ListCrudRepository<CoupleRow, UUID>
{
	// SELECT ... FOR UPDATE: the row stays locked until the end of the transaction.
	@Lock(LockMode.PESSIMISTIC_WRITE)
	Optional<CoupleRow> findLockedById(UUID id);

	@Query("SELECT * FROM couple WHERE (partner_one_id = :patient OR partner_two_id = :patient) AND ended_at IS NULL")
	Optional<CoupleRow> findActiveOf(@Param("patient") UUID patient);

	@Query("SELECT * FROM couple WHERE therapist_id = :therapist AND ended_at IS NULL ORDER BY id FOR UPDATE")
	List<CoupleRow> findActiveOfTherapistLocked(@Param("therapist") UUID therapist);

	@Query("SELECT * FROM couple WHERE ended_at < :instant ORDER BY ended_at")
	List<CoupleRow> findEndedBefore(@Param("instant") LocalDateTime instant);
}
