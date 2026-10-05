package com.aemotionalline.domain.message;

import java.util.Objects;
import java.util.UUID;

/** Identity of a paragraph, the node of the conversation graph. */
public record ParagraphId(UUID value)
{
	public ParagraphId
	{
		Objects.requireNonNull(value, "ParagraphId cannot be null.");
	}

	/** The domain creates its own ids, so an aggregate never has to wait for the database to have one. */
	public static ParagraphId random()
	{
		return new ParagraphId(UUID.randomUUID());
	}
}
