package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.Objects;

import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.user.UserId;

/**
 * One paragraph of a topic history, with where it was written (discussion and message), by whom and when, so the
 * archive can group the history by discussion or by message without looking anything else up. The paragraph is sealed.
 */
public record TopicHistoryEntry(Paragraph paragraph, DiscussionId discussionId, MessageId messageId, UserId sender, Instant sentAt)
{
	public TopicHistoryEntry
	{
		Objects.requireNonNull(paragraph);
		Objects.requireNonNull(discussionId);
		Objects.requireNonNull(messageId);
		Objects.requireNonNull(sender);
		Objects.requireNonNull(sentAt);
	}
}
