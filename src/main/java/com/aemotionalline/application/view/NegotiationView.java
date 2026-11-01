package com.aemotionalline.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.NegotiationStatus;
import com.aemotionalline.domain.user.UserId;

/**
 * A negotiation as one viewer sees it: what it is for, its state, the request and the proposals sent. The draft in
 * progress is shown only to its author: an unsent draft is work in progress, not part of the exchange.
 */
public record NegotiationView(
		NegotiationId id,
		CoupleId coupleId,
		NegotiationRecord.Purpose purpose,
		Optional<ParagraphId> originParagraphId,
		NegotiationStatus status,
		UserId currentResponder,
		ProposalView request,
		List<ProposalView> sentProposals,
		Optional<ProposalView> draft,
		Instant startedAt,
		Optional<UserId> closedBy,
		Optional<Instant> closedAt)
{
	public NegotiationView
	{
		sentProposals = List.copyOf(sentProposals);
	}
}
