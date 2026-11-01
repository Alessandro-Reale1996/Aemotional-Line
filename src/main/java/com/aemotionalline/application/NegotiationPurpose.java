package com.aemotionalline.application;

import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.NegotiationId;

/**
 * What a negotiation in progress is for, kept by the application next to it (the domain's Negotiation deliberately
 * doesn't know): starting the conversation, changing the agreement or opening a discussion, and for a discussion the
 * paragraph it will expand, if any. Fixed when the negotiation starts, used when it ends to apply its effect or record
 * its failure.
 */
public record NegotiationPurpose(NegotiationId negotiationId, NegotiationRecord.Purpose purpose, Optional<ParagraphId> origin)
{
	public NegotiationPurpose
	{
		Objects.requireNonNull(negotiationId);
		Objects.requireNonNull(purpose);
		Objects.requireNonNull(origin);

		if (origin.isPresent() && purpose != NegotiationRecord.Purpose.OPENED_DISCUSSION)
		{
			throw new IllegalArgumentException("Only a negotiation that opens a discussion can have an origin paragraph.");
		}
	}
}
