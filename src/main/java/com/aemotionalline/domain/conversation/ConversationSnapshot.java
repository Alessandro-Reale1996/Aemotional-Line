package com.aemotionalline.domain.conversation;

import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.constraint.ConstraintRequestRecord;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.MessageId;

/**
 * The stored facts of a conversation, from which the persistence layer rebuilds it with {@link Conversation#restore}.
 * Only facts: the graph (from the messages, in {@code sendingOrder}), the agreement in force (the final proposal of the
 * last start or change of agreement) and the constraints in force (those of the last applied request) are derived.
 */
public record ConversationSnapshot(
		ConversationId id,
		Couple couple,
		List<DiscussionSnapshot> discussions,
		List<MessageId> sendingOrder,
		List<NegotiationRecord> negotiationHistory,
		List<FailedNegotiationRecord> failedNegotiations,
		List<ConstraintRequestRecord> constraintRequests)
{
	public ConversationSnapshot
	{
		Objects.requireNonNull(id);
		Objects.requireNonNull(couple);
		discussions = List.copyOf(discussions);
		sendingOrder = List.copyOf(sendingOrder);
		negotiationHistory = List.copyOf(negotiationHistory);
		failedNegotiations = List.copyOf(failedNegotiations);
		constraintRequests = List.copyOf(constraintRequests);
	}
}
