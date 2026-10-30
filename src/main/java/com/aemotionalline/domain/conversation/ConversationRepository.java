package com.aemotionalline.domain.conversation;

import java.util.Optional;

import com.aemotionalline.domain.couple.CoupleId;

/**
 * Where conversations are kept; a couple has one conversation. A conversation is loaded whole, because its rules need
 * the graph and the discussions; saving it writes only what changed. Plain Java: the persistence layer implements it.
 */
public interface ConversationRepository
{
	Optional<Conversation> findById(ConversationId id);

	/**
	 * Like {@link #findById}, but locks the conversation until the end of the transaction, so that two operations on it
	 * can't overlap: a concurrent one waits and then sees the updated conversation, and the domain rules decide (e.g. a
	 * message to a discussion closed meanwhile is rejected). Used by every operation that changes it.
	 */
	Optional<Conversation> findByIdForUpdate(ConversationId id);

	Optional<Conversation> findByCouple(CoupleId coupleId);

	void save(Conversation conversation);
}
