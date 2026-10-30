package com.aemotionalline.infrastructure.persistence.negotiation;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The header of a negotiation in progress (or of a failed attempt to start a couple's conversation). The request, a frozen
 * copy of the initial proposal, is kept here; the proposals are rows of their own.
 */
@Table("negotiation")
public record NegotiationRow(
		@Id UUID id,
		UUID coupleId,
		String status,
		UUID currentResponder,
		UUID requestId,
		UUID requestAuthor,
		String requestKind,
		String requestText,
		String requestTitle,
		String requestSubtitle,
		LocalDateTime startedAt,
		UUID closedBy,
		LocalDateTime closedAt)
{
}
