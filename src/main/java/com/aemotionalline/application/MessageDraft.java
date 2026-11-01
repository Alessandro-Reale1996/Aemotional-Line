package com.aemotionalline.application;

import java.util.List;

/** A message the client wants to send: its paragraphs, in order. The server creates the message's id. */
public record MessageDraft(List<ParagraphDraft> paragraphs)
{
	public MessageDraft
	{
		paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
	}
}
