package com.aemotionalline.application;

import java.util.List;
import java.util.Objects;

/** A message the client wants to send: its paragraphs, in order. The server creates the message's id. */
public record MessageDraft(List<ParagraphDraft> paragraphs)
{
	public MessageDraft
	{
		if (paragraphs != null && paragraphs.stream().anyMatch(Objects::isNull))
		{
			throw new InvalidRequestException("A paragraph of the draft is missing.");
		}

		paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
	}
}
