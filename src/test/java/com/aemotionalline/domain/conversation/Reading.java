package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.List;

import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.message.Message;

/**
 * Test helper: a reply requires the message being answered to be read first, so tests that are not about reading
 * send through here, which reads the last message of the discussion as the sender of the reply and then sends.
 */
final class Reading
{
	static final Instant READ_AT = Instant.EPOCH;

	private Reading()
	{
	}

	static void send(Conversation conversation, Message message, DiscussionId discussionId, ConstraintContext context)
	{
		List<Message> sent = conversation.findDiscussion(discussionId).getMessages();

		if (!sent.isEmpty() && !sent.getLast().getSenderId().equals(message.getSenderId()))
		{
			conversation.readMessage(message.getSenderId(), discussionId, sent.getLast().getId(), new ConstraintContext(context.time(), READ_AT));
		}

		conversation.sendMessage(message, discussionId, context);
	}
}
