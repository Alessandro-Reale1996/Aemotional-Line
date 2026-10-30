package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.DiscussionTitle;

/**
 * The stored facts of a discussion: its messages (sealed, in order), when each was first read, its closing requests
 * and its closure. Its last sender is derived from the last message. Part of a {@link ConversationSnapshot}.
 */
public record DiscussionSnapshot(
		DiscussionId id,
		Optional<DiscussionTitle> title,
		Optional<ParagraphId> originParagraphId,
		List<Message> messages,
		Map<MessageId, Instant> readAt,
		List<ClosingRequestRecord> closingRequests,
		List<TherapistClosingRequestRecord> therapistClosingRequests,
		Optional<DiscussionClosure> closure)
{
	public DiscussionSnapshot
	{
		Objects.requireNonNull(id);
		Objects.requireNonNull(title);
		Objects.requireNonNull(originParagraphId);
		Objects.requireNonNull(closure);
		messages = List.copyOf(messages);
		readAt = Map.copyOf(readAt);
		closingRequests = List.copyOf(closingRequests);
		therapistClosingRequests = List.copyOf(therapistClosingRequests);
	}
}
