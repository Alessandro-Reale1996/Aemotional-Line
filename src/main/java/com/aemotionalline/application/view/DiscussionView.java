package com.aemotionalline.application.view;

import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.user.UserId;

/** A discussion as one viewer sees it: the main one has no title; a specific one may expand an origin paragraph. */
public record DiscussionView(DiscussionId id, Optional<String> title, Optional<String> subtitle, Optional<ParagraphId> originParagraphId,
		boolean closed, Optional<UserId> lastSender, List<MessageView> messages)
{
	public DiscussionView
	{
		messages = List.copyOf(messages);
	}
}
