package com.aemotionalline.domain.negotiation;

import java.util.Objects;
import java.util.UUID;

/** Identity of a negotiation. It is an object (not a primitive), so a missing id is detected instead of becoming 0. */
public record NegotiationId(UUID value)
{
	public NegotiationId
	{
		Objects.requireNonNull(value, "NegotiationId cannot be null.");
	}

	/** The domain creates its own ids, so an aggregate never has to wait for the database to have one. */
	public static NegotiationId random()
	{
		return new NegotiationId(UUID.randomUUID());
	}
}
