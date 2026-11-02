package com.aemotionalline.application;

import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.user.UserId;

/** Loading a conversation for a member of its couple: with the lock for a change, without it for a read. */
final class ConversationAccess
{
	private ConversationAccess()
	{
	}

	static Conversation locked(ConversationRepository conversations, UserId user, ConversationId id)
	{
		Conversation conversation = conversations.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Conversation not found."));
		Access.ensureMember(conversation.getCouple(), user, "Conversation");

		return conversation;
	}

	static Conversation readable(ConversationRepository conversations, UserId user, ConversationId id)
	{
		Conversation conversation = conversations.findById(id).orElseThrow(() -> new NotFoundException("Conversation not found."));
		Access.ensureMember(conversation.getCouple(), user, "Conversation");

		return conversation;
	}
}
