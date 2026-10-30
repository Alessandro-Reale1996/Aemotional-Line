package com.aemotionalline.infrastructure.persistence.archive;

import java.time.Instant;

import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalContent;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.negotiation.ProposalStatus;
import com.aemotionalline.domain.user.UserId;

/**
 * A proposal found in the archive (an agreement text or a discussion title), with the negotiation it belongs to, what that
 * negotiation was for and whether it took effect or failed. sentAt is null for the request of a failed negotiation that
 * sent nothing.
 */
public record ArchiveProposal(
		NegotiationId negotiationId,
		NegotiationRecord.Purpose purpose,
		Section section,
		ProposalId proposalId,
		UserId author,
		ProposalContent content,
		Instant sentAt,
		ProposalStatus status)
{
	public enum Section
	{
		/** The negotiation took effect (started the conversation, changed the agreement, opened a discussion). */
		TOOK_EFFECT,
		/** The negotiation ended without an agreement. */
		FAILED
	}
}
