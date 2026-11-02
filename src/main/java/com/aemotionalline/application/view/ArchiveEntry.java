package com.aemotionalline.application.view;

import java.time.Instant;
import java.util.Optional;

import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.user.UserId;

/**
 * One line of the archive (browsing by time, keyword search, topic history, unanswered questions): a paragraph with where,
 * when and by whom it was written, or a closed envelope for a message of the other partner the viewer hasn't opened, which
 * shows only the message, its sender and instant (no paragraph id, type or text, not even how many paragraphs it has).
 * A pointed paragraph's title is computed from its question; where it comes from the database it is null.
 */
public record ArchiveEntry(
		Optional<ParagraphId> paragraphId,
		ParagraphType type,
		String title,
		String subtitle,
		String body,
		DiscussionId discussionId,
		MessageId messageId,
		UserId sender,
		Instant sentAt,
		boolean closedEnvelope)
{
	public static ArchiveEntry envelope(DiscussionId discussionId, MessageId messageId, UserId sender, Instant sentAt)
	{
		return new ArchiveEntry(Optional.empty(), null, null, null, null, discussionId, messageId, sender, sentAt, true);
	}
}
