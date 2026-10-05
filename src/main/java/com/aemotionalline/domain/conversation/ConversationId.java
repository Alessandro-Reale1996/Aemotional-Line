package com.aemotionalline.domain.conversation;

import java.util.Objects;
import java.util.UUID;

/** Identity of a conversation, a type of its own so it cannot be confused with a couple or user id. */
public record ConversationId(UUID value)
{
	public ConversationId
	{
		Objects.requireNonNull(value, "ConversationId cannot be null.");
	}

	/** The domain creates its own ids, so an aggregate never has to wait for the database to have one. */
	public static ConversationId random()
	{
		return new ConversationId(UUID.randomUUID());
	}
}
