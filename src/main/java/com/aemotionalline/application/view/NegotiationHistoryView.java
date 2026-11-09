package com.aemotionalline.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.FailedNegotiationRecord;
import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.user.UserId;

/**
 * The negotiations of a conversation that are over (spec 1.3.1: every step of the stipula can be consulted), oldest first,
 * in two sections: those that took effect and those that failed. Each keeps the proposals that were sent; a failed one that
 * sent nothing keeps its request. Drafts are never kept. The same for the couple's three members.
 */
public record NegotiationHistoryView(List<TookEffect> tookEffect, List<Failed> failed)
{
	public NegotiationHistoryView
	{
		tookEffect = List.copyOf(tookEffect);
		failed = List.copyOf(failed);
	}

	/** {@code discussionId} is the discussion it opened or, for the start of the conversation, the main one. */
	public record TookEffect(NegotiationId negotiationId, NegotiationRecord.Purpose purpose, Optional<DiscussionId> discussionId, Instant archivedAt,
			List<ProposalView> proposals)
	{
		public TookEffect
		{
			proposals = List.copyOf(proposals);
		}
	}

	public record Failed(NegotiationId negotiationId, NegotiationRecord.Purpose attemptedPurpose, FailedNegotiationRecord.Outcome outcome, UserId closedBy,
			Instant startedAt, Instant closedAt, List<ProposalView> proposals)
	{
		public Failed
		{
			proposals = List.copyOf(proposals);
		}
	}
}
