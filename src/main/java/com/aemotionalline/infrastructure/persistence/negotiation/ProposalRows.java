package com.aemotionalline.infrastructure.persistence.negotiation;

import java.util.UUID;

import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.negotiation.ProposalContent;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.negotiation.ProposalStatus;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.persistence.Utc;

/** Proposal to row and back. The kind of content is stored as AGREEMENT or DISCUSSION_TITLE. */
public final class ProposalRows
{
	static final String AGREEMENT = "AGREEMENT";
	static final String DISCUSSION_TITLE = "DISCUSSION_TITLE";

	private ProposalRows()
	{
	}

	public static ProposalRow toRow(Proposal proposal, UUID negotiationId, Integer position)
	{
		ProposalContent content = proposal.getContent();

		return switch (content)
		{
			case AgreementText agreement -> new ProposalRow(proposal.getId().value(), negotiationId, position, proposal.getAuthor().value(),
					AGREEMENT, agreement.text(), null, null, proposal.getJustification(), Utc.toColumn(proposal.getSentAt()),
					proposal.getProposalStatus().name());
			case DiscussionTitle title -> new ProposalRow(proposal.getId().value(), negotiationId, position, proposal.getAuthor().value(),
					DISCUSSION_TITLE, null, title.title(), title.subtitle(), proposal.getJustification(), Utc.toColumn(proposal.getSentAt()),
					proposal.getProposalStatus().name());
		};
	}

	public static Proposal toDomain(ProposalRow row)
	{
		return Proposal.restore(new ProposalId(row.id()), new UserId(row.authorId()), content(row.kind(), row.text(), row.title(), row.subtitle()),
				row.justification(), Utc.fromColumn(row.sentAt()), ProposalStatus.valueOf(row.status()));
	}

	static ProposalContent content(String kind, String text, String title, String subtitle)
	{
		return switch (kind)
		{
			case AGREEMENT -> new AgreementText(text);
			case DISCUSSION_TITLE -> new DiscussionTitle(title, subtitle);
			default -> throw new IllegalStateException("Unknown proposal kind in the database: " + kind);
		};
	}
}
