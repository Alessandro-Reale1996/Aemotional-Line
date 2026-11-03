package com.aemotionalline.domain.negotiation;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;

/** The text of the agreement (accordo). It can never be empty. */
public record AgreementText(String text) implements ProposalContent
{
	public AgreementText
	{
		if (text == null || text.isBlank())
		{
			throw new DomainException(ErrorCode.TEXT_REQUIRED, "The proposal text cannot be blank.");
		}
	}
}
