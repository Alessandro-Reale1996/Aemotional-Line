package com.aemotionalline.domain.negotiation;

import java.util.Objects;

public record ProposalId(Long value) 
{
	public ProposalId
	{
		Objects.requireNonNull(value);
	}
}
