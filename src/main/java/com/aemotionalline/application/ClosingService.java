package com.aemotionalline.application;

import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.view.ClosingView;
import com.aemotionalline.application.view.Views;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.OutcomeDeclaration;
import com.aemotionalline.domain.user.UserId;

/**
 * Closing a specific discussion: nobody closes it alone (the other partner accepts a request, or the therapist confirms a
 * special request after a failed attempt), then each partner declares whether its topic was resolved or stalled. The rules
 * are the domain's; this service locks the conversation, checks membership and saves.
 */
@Service
@Transactional
public class ClosingService
{
	private final ConversationRepository conversations;

	ClosingService(ConversationRepository conversations)
	{
		this.conversations = conversations;
	}

	public void requestClosing(UserId partner, ConversationId id, int discussionNumber)
	{
		change(partner, id, conversation -> conversation.requestClosing(new DiscussionId(id, discussionNumber), partner));
	}

	public void acceptClosing(UserId partner, ConversationId id, int discussionNumber)
	{
		change(partner, id, conversation -> conversation.acceptClosing(new DiscussionId(id, discussionNumber), partner));
	}

	public void refuseClosing(UserId partner, ConversationId id, int discussionNumber)
	{
		change(partner, id, conversation -> conversation.refuseClosing(new DiscussionId(id, discussionNumber), partner));
	}

	public void withdrawClosing(UserId partner, ConversationId id, int discussionNumber)
	{
		change(partner, id, conversation -> conversation.withdrawClosing(new DiscussionId(id, discussionNumber), partner));
	}

	public void requestTherapistClosing(UserId partner, ConversationId id, int discussionNumber, String justification)
	{
		change(partner, id, conversation -> conversation.requestTherapistClosing(new DiscussionId(id, discussionNumber), partner, justification));
	}

	public void confirmTherapistClosing(UserId therapist, ConversationId id, int discussionNumber)
	{
		change(therapist, id, conversation -> conversation.confirmTherapistClosing(new DiscussionId(id, discussionNumber), therapist));
	}

	public void refuseTherapistClosing(UserId therapist, ConversationId id, int discussionNumber)
	{
		change(therapist, id, conversation -> conversation.refuseTherapistClosing(new DiscussionId(id, discussionNumber), therapist));
	}

	public void declareOutcome(UserId partner, ConversationId id, int discussionNumber, OutcomeDeclaration.Outcome outcome, String reason)
	{
		change(partner, id, conversation -> conversation.declareOutcome(new DiscussionId(id, discussionNumber), partner, outcome, reason));
	}

	/** The closing of a discussion as the viewer sees it (see ClosingView for what each one may see). */
	@Transactional(readOnly = true)
	public ClosingView view(UserId viewer, ConversationId id, int discussionNumber)
	{
		Conversation conversation = ConversationAccess.readable(conversations, viewer, id);

		return Views.closing(conversation.findDiscussion(new DiscussionId(id, discussionNumber)), conversation.getCouple(), viewer);
	}

	private void change(UserId user, ConversationId id, Consumer<Conversation> change)
	{
		Conversation conversation = ConversationAccess.locked(conversations, user, id);
		change.accept(conversation);
		conversations.save(conversation);
	}
}
