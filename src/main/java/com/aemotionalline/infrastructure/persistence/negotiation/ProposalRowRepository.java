package com.aemotionalline.infrastructure.persistence.negotiation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface ProposalRowRepository extends ListCrudRepository<ProposalRow, UUID>
{
	List<ProposalRow> findByNegotiationIdAndPositionIsNotNullOrderByPosition(UUID negotiationId);

	Optional<ProposalRow> findByNegotiationIdAndPositionIsNull(UUID negotiationId);

	@Modifying
	@Query("DELETE FROM proposal WHERE negotiation_id = :negotiationId")
	void deleteAllOfNegotiation(@Param("negotiationId") UUID negotiationId);

	// The draft in progress has no position; the sent proposals stay, owned by the conversation's record.
	@Modifying
	@Query("DELETE FROM proposal WHERE negotiation_id = :negotiationId AND position IS NULL")
	void deleteDraftOf(@Param("negotiationId") UUID negotiationId);
}
