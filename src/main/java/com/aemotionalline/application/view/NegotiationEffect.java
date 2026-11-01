package com.aemotionalline.application.view;

import java.util.Optional;

import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.DiscussionId;

/** What an accepted negotiation did: the conversation it started or changed, and the discussion it opened, if any. */
public record NegotiationEffect(ConversationId conversationId, Optional<DiscussionId> openedDiscussion)
{
}
