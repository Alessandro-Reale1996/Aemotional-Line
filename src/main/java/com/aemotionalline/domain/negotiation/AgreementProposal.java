package com.aemotionalline.domain.negotiation;

import com.aemotionalline.domain.user.UserId;

/** One version of the agreement text. */
public class AgreementProposal extends Proposal
{
	public AgreementProposal(ProposalId id, UserId author, String text)
	{
		super(id, author, new AgreementText(text));
	}
	
	public String getText()
	{
		return ((AgreementText) getContent()).text();
	}

	@Override
	Proposal draftFor(ProposalId newId, UserId newAuthor)
	{
		return new AgreementProposal(newId, newAuthor, getText());
	}

	@Override
	boolean acceptsContent(ProposalContent content)
	{
		return content instanceof AgreementText;
	}
}
