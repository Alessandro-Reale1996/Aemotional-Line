package com.aemotionalline.infrastructure.persistence.archive;

import java.time.Instant;

import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.user.UserId;

/**
 * A paragraph found in the archive, read straight from the tables (no domain object): its text, and where, when and by
 * whom it was written. With its id the topic history can be asked for (TopicHistory). A pointed paragraph's title is
 * computed from its question and isn't stored, so it is null here.
 */
public record ArchiveParagraph(
		ParagraphId paragraphId,
		ParagraphType type,
		String title,
		String subtitle,
		String body,
		DiscussionId discussionId,
		MessageId messageId,
		UserId sender,
		Instant sentAt)
{
}
