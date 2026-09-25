package com.aemotionalline.domain.negotiation;

import java.util.Objects;

public record NegotiationId(long id) 
{
	public NegotiationId 
	{
		Objects.requireNonNull(id);
    }
}
