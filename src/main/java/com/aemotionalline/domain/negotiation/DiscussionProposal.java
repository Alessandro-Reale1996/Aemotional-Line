package com.aemotionalline.domain.negotiation;

import com.aemotionalline.domain.user.UserId;

/** One version of the title and subtitle of a specific discussion. */
public class DiscussionProposal extends Proposal
{
	public DiscussionProposal(ProposalId id, UserId author, String title, String subtitle)
	{
		super(id, author, new DiscussionTitle(title, subtitle));
	}
	
	public String getTitle()
	{
		return ((DiscussionTitle) getContent()).title();
	}
	
	public String getSubtitle()
	{
		return ((DiscussionTitle) getContent()).subtitle();
	}

	@Override
	Proposal draftFor(ProposalId newId, UserId newAuthor)
	{
		return new DiscussionProposal(newId, newAuthor, getTitle(), getSubtitle());
	}

	@Override
	boolean acceptsContent(ProposalContent content)
	{
		return content instanceof DiscussionTitle;
	}
}
