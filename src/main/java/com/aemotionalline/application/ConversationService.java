package com.aemotionalline.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.view.ConversationView;
import com.aemotionalline.application.view.Views;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.user.UserId;

/** The conversation: viewing it, reading and sending messages, previews for the pop-ups. */
@Service
@Transactional
public class ConversationService
{
	private final ConversationRepository conversations;

	ConversationService(ConversationRepository conversations)
	{
		this.conversations = conversations;
	}

	/** The conversation as the viewer sees it: the other partner's messages not opened yet are closed envelopes. */
	@Transactional(readOnly = true)
	public ConversationView view(UserId viewer, ConversationId id)
	{
		Conversation conversation = conversations.findById(id).orElseThrow(() -> new NotFoundException("Conversation not found."));

		Access.ensureMember(conversation.getCouple(), viewer, "Conversation");

		return Views.conversation(conversation, viewer);
	}
}
