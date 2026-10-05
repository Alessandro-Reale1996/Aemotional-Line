package com.aemotionalline.domain.user;

import java.util.Objects;
import java.util.UUID;

/** A dedicated type instead of a bare UUID so a user's id cannot be mixed up with the other ids of the domain. */
public record UserId(UUID value)
{
	public UserId
	{
		Objects.requireNonNull(value, "UserId cannot be null.");
	}

	/** The domain creates its own ids, so an aggregate never has to wait for the database to have one. */
	public static UserId random()
	{
		return new UserId(UUID.randomUUID());
	}
}
