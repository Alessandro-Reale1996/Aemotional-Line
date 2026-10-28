package com.aemotionalline.domain.constraint;

import java.util.Objects;
import java.util.UUID;

/** Identity of a constraint change request, so the partners can approve or reject a specific one. */
public record ConstraintChangeRequestId(UUID value)
{
	public ConstraintChangeRequestId
	{
		Objects.requireNonNull(value, "ConstraintChangeRequestId cannot be null.");
	}

	/** The domain creates its own ids, so an aggregate never has to wait for the database to have one. */
	public static ConstraintChangeRequestId random()
	{
		return new ConstraintChangeRequestId(UUID.randomUUID());
	}
}
