package com.aemotionalline.application.view;

import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.negotiation.NegotiationId;

/**
 * A proposal found in the archive (an agreement text or a discussion title), with the negotiation it belongs to, what that
 * negotiation was for and whether it took effect or failed. Its sentAt is null for the request of a failed negotiation that
 * sent nothing.
 */
public record ArchiveProposal(
		NegotiationId negotiationId,
		NegotiationRecord.Purpose purpose,
		Section section,
		ProposalView proposal)
{
	public enum Section
	{
		/** The negotiation took effect (started the conversation, changed the agreement, opened a discussion). */
		TOOK_EFFECT,
		/** The negotiation ended without an agreement. */
		FAILED
	}
}
