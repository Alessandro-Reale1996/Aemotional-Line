package com.aemotionalline.domain.message;

import java.util.Objects;
import java.util.UUID;

import com.aemotionalline.domain.common.UuidV7;

/** Identity of a message, a type of its own so it cannot be confused with the other ids. */
public record MessageId(UUID value)
{
	public MessageId
	{
		Objects.requireNonNull(value, "MessageId cannot be null.");
	}

	/** The domain creates its own ids, so an aggregate never has to wait for the database to have one. */
	public static MessageId random()
	{
		return new MessageId(UuidV7.generate());
	}
}
