package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.Proposal;

/**
 * One entry of a conversation's negotiation history (spec 1.3.1: every step of the stipula is tracked and can be
 * consulted in the archive). A negotiation is transitory: only its history is kept, the proposals that were sent, in
 * order, each with its justification; the last one is the agreement or title that took effect. The record adds what
 * the negotiation was used for, the discussion it opened (if any) and when it took effect.
 */
public record NegotiationRecord(NegotiationId negotiationId, Purpose purpose, Optional<DiscussionId> discussionId, Instant archivedAt, List<Proposal> proposals)
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
		Objects.requireNonNull(negotiationId);
		Objects.requireNonNull(purpose);
		Objects.requireNonNull(discussionId);
		Objects.requireNonNull(archivedAt);
		proposals = List.copyOf(proposals);

		if (proposals.isEmpty())
		{
			throw new DomainException("A negotiation that took effect has sent at least one proposal.");
		}
	}

	/** The proposal that took effect: the agreement, or the title of the discussion. */
	public Proposal finalProposal()
	{
		return proposals.getLast();
	}
}
