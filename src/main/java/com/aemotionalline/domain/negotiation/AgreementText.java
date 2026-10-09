package com.aemotionalline.domain.negotiation;

import com.aemotionalline.domain.common.DomainException;

/** The text of the agreement (accordo). It can never be empty. */
public record AgreementText(String text) implements ProposalContent
{
	public AgreementText
	{
		if (text == null || text.isBlank())
		{
			throw new DomainException("The proposal text cannot be blank.");
		}
	}
}
