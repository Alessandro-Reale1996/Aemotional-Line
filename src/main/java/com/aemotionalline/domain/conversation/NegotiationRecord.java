package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.negotiation.Negotiation;

/**
 * One entry of a conversation's negotiation history (spec 1.3.1: every step of the stipula is tracked and can be
 * consulted in the archive). The negotiation keeps every proposal with its justification; the record adds what the
 * negotiation was used for, the discussion it opened (if any) and when it took effect. A recorded negotiation is
 * concluded, so it can no longer change.
 */
public record NegotiationRecord(Negotiation negotiation, Purpose purpose, Optional<DiscussionId> discussionId, Instant archivedAt)
{
	public enum Purpose
	{
		/** Set the first agreement and opened the main discussion. */
		STARTED_CONVERSATION,
		/** Replaced the agreement. */
		MODIFIED_AGREEMENT,
		/** Set the title and subtitle of a new discussion. */
		OPENED_DISCUSSION
	}

	public NegotiationRecord
	{
		Objects.requireNonNull(negotiation);
		Objects.requireNonNull(purpose);
		Objects.requireNonNull(discussionId);
		Objects.requireNonNull(archivedAt);
	}
}
