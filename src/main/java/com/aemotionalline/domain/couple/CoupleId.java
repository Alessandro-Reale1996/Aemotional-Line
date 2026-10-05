package com.aemotionalline.domain.couple;

import java.util.Objects;
import java.util.UUID;

/** Identity of a couple, a type of its own so it cannot be confused with a conversation or user id. */
public record CoupleId(UUID value)
{
	public CoupleId
	{
		Objects.requireNonNull(value, "CoupleId cannot be null.");
	}

	/** The domain creates its own ids, so an aggregate never has to wait for the database to have one. */
	public static CoupleId random()
	{
		return new CoupleId(UUID.randomUUID());
	}
}
