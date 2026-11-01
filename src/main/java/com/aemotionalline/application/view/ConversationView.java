package com.aemotionalline.application.view;

import java.util.List;

import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.couple.CoupleId;

/** A conversation as one viewer sees it: the agreement in force and the discussions in opening order. */
public record ConversationView(ConversationId id, CoupleId coupleId, String agreement, List<DiscussionView> discussions)
{
	public ConversationView
	{
		discussions = List.copyOf(discussions);
	}
}
