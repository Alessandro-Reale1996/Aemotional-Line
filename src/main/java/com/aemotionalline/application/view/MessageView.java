package com.aemotionalline.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.user.UserId;

/**
 * A message as one viewer sees it. A closed envelope (a message of the other partner the viewer hasn't opened yet) shows
 * only who sent it and when: its paragraphs and skipped questions are empty, since a read constraint must not be bypassed.
 * {@code readAt} is when the recipient first opened it.
 */
public record MessageView(MessageId id, UserId sender, Instant sentAt, boolean closedEnvelope, Optional<Instant> readAt,
		List<ParagraphView> paragraphs, List<ParagraphId> questionsLeftUnanswered)
{
	public MessageView
	{
		paragraphs = List.copyOf(paragraphs);
		questionsLeftUnanswered = List.copyOf(questionsLeftUnanswered);
	}
}
