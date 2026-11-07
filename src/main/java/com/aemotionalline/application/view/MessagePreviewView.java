package com.aemotionalline.application.view;

import java.util.List;

import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.message.ParagraphId;

/**
 * What the pop-ups need before a message is sent: the questions of the message being answered that the draft skips (a
 * warning: the message can still be sent), and the discussions that keep it from being sent here (it must be written there).
 */
public record MessagePreviewView(List<ParagraphId> questionsLeftUnanswered, List<DiscussionId> blockingDiscussions)
{
	public MessagePreviewView
	{
		questionsLeftUnanswered = List.copyOf(questionsLeftUnanswered);
		blockingDiscussions = List.copyOf(blockingDiscussions);
	}
}
