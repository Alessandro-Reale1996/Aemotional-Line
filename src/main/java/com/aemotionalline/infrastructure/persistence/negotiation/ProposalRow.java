package com.aemotionalline.infrastructure.persistence.negotiation;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A row of the proposal table, shared by negotiations in progress and by the records of a conversation. A sent proposal
 * has a position; the draft in progress has none (null).
 */
@Table("proposal")
public record ProposalRow(
		@Id UUID id,
		UUID negotiationId,
		Integer position,
		UUID authorId,
		String kind,
		String text,
		String title,
		String subtitle,
		String justification,
		LocalDateTime sentAt,
		String status)
{
}
