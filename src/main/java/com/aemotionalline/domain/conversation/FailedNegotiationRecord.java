package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;

/**
 * A negotiation of the conversation that ended without an agreement, kept in its own section of the archive so the
 * history of what took effect stays clean. It keeps the proposals that were sent, in order; when none was sent (a
 * refused request, or one abandoned before the answer) it keeps the request, since that is what the other partner
 * received. The draft in progress is never kept.
 */
public record FailedNegotiationRecord(
		NegotiationId negotiationId,
		NegotiationRecord.Purpose attemptedPurpose,
		Outcome outcome,
		UserId closedBy,
		Instant startedAt,
		Instant closedAt,
		List<Proposal> proposals)
{
	public enum Outcome
	{
		/** The other partner refused the request to talk. */
		REFUSED,
		/** A partner gave up before an agreement. */
		ABANDONED
	}

	public FailedNegotiationRecord
	{
		Objects.requireNonNull(negotiationId);
		Objects.requireNonNull(attemptedPurpose);
		Objects.requireNonNull(outcome);
		Objects.requireNonNull(closedBy);
		Objects.requireNonNull(startedAt);
		Objects.requireNonNull(closedAt);
		proposals = List.copyOf(proposals);

		if (proposals.isEmpty())
		{
			throw new DomainException("A failed negotiation keeps at least its request.");
		}
	}
}
