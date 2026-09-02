package com.aemotionalline.domain.negotiation;

import java.util.Objects;

public record NegotiationId(Long value) 
{
	public NegotiationId
	{
		Objects.requireNonNull(value);
	}
}
