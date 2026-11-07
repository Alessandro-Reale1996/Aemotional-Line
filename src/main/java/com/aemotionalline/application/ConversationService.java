package com.aemotionalline.application;

import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.view.ConversationView;
import com.aemotionalline.application.view.MessagePreviewView;
import com.aemotionalline.application.view.MessageView;
import com.aemotionalline.application.view.Views;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.Discussion;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.PointedParagraph;
import com.aemotionalline.domain.message.QuestionParagraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.user.UserId;

/**
 * The conversation: viewing it, opening (reading) a message, sending one from a draft, and the previews for the
 * pop-ups. The client sends ids; they are turned into the conversation's paragraphs here, and a paragraph the user can't
 * see (in the other partner's unopened message) is treated as missing. The local time of day and the position, for the
 * constraints, come from the client; the instant comes from the server's clock, inside the domain.
 */
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
	@ReadOnlyTransaction
	public ConversationView view(UserId viewer, ConversationId id)
	{
		return Views.conversation(readable(viewer, id), viewer);
	}

	/**
	 * Opens a message: the reader's read constraints are checked and the first opening is recorded (a reply needs it).
	 * Returns the message with its content. The therapist and the sender just read it, nothing is recorded.
	 */
	public MessageView readMessage(UserId reader, ConversationId id, int discussionNumber, MessageId messageId, LocalTime localTime,
			GeoPosition position)
	{
		Conversation conversation = locked(reader, id);
		DiscussionId discussionId = new DiscussionId(id, discussionNumber);

		conversation.readMessage(reader, discussionId, messageId, new ConstraintContext(localTime, position));
		conversations.save(conversation);

		Discussion discussion = conversation.findDiscussion(discussionId);
		Message message = discussion.getMessages().stream().filter(sent -> sent.getId().equals(messageId)).findFirst().orElseThrow();

		return Views.message(message, discussion, conversation.getCouple(), reader);
	}

	/** Sends a message built from the draft; every rule of the domain applies. Returns the new message's id. */
	public MessageId sendMessage(UserId sender, ConversationId id, int discussionNumber, MessageDraft draft, LocalTime localTime,
			GeoPosition position)
	{
		Conversation conversation = locked(sender, id);
		Message message = build(sender, conversation, draft);

		conversation.sendMessage(message, new DiscussionId(id, discussionNumber), new ConstraintContext(localTime, position));
		conversations.save(conversation);

		return message.getId();
	}

	/**
	 * For the pop-ups before sending, from one read of the conversation: the questions of the message being answered the
	 * draft skips, and the discussions that keep it from being sent here. Only questions the sender can see are listed, or
	 * the preview would reveal how many a closed envelope holds.
	 */
	@ReadOnlyTransaction
	public MessagePreviewView preview(UserId sender, ConversationId id, int discussionNumber, MessageDraft draft)
	{
		Conversation conversation = readable(sender, id);
		Message message = build(sender, conversation, draft);
		DiscussionId discussionId = new DiscussionId(id, discussionNumber);

		List<ParagraphId> questions = conversation.questionsLeftUnansweredBy(message, discussionId).stream()
				.map(Paragraph::getId)
				.filter(question -> Paragraphs.isVisibleTo(sender, conversation, question))
				.toList();

		return new MessagePreviewView(questions, conversation.discussionsBlockingReplyTo(message, discussionId));
	}

	private Conversation locked(UserId user, ConversationId id)
	{
		return ConversationAccess.locked(conversations, user, id);
	}

	private Conversation readable(UserId user, ConversationId id)
	{
		return ConversationAccess.readable(conversations, user, id);
	}

	// The draft's ids become the conversation's paragraphs (only the ones the sender can see); the server creates the new ids.
	private static Message build(UserId sender, Conversation conversation, MessageDraft draft)
	{
		if (draft == null)
		{
			throw new InvalidRequestException("A message draft is required.");
		}

		Message message = new Message(MessageId.random(), sender);

		for (ParagraphDraft paragraphDraft : draft.paragraphs())
		{
			if (paragraphDraft.type() == null)
			{
				throw new InvalidRequestException("Every paragraph of the draft needs a type.");
			}

			ensureOnlyFieldsOfItsType(paragraphDraft);

			Paragraph paragraph = switch (paragraphDraft.type())
			{
				case SIMPLE -> new SimpleParagraph(ParagraphId.random(), paragraphDraft.title(), paragraphDraft.subtitle(), paragraphDraft.body());
				case QUESTION -> new QuestionParagraph(ParagraphId.random(), paragraphDraft.title(), paragraphDraft.body());
				case POINTED -> new PointedParagraph(ParagraphId.random(), question(sender, conversation, paragraphDraft), paragraphDraft.body());
			};

			if (!(paragraph instanceof PointedParagraph))
			{
				paragraphDraft.references().forEach(reference -> paragraph.addReference(Paragraphs.visibleTo(sender, conversation, reference)));
			}

			paragraphDraft.selfCitations().forEach(cited -> paragraph.addSelfCitation(Paragraphs.visibleTo(sender, conversation, cited)));

			message.addParagraph(paragraph);
		}

		return message;
	}

	// A field the paragraph's type doesn't have would be lost, and the server never alters what the user wrote.
	private static void ensureOnlyFieldsOfItsType(ParagraphDraft draft)
	{
		switch (draft.type())
		{
			case SIMPLE ->
			{
				if (draft.question().isPresent())
				{
					throw new InvalidRequestException("A simple paragraph doesn't take a question.");
				}
			}
			case QUESTION ->
			{
				if (draft.subtitle() != null || draft.question().isPresent())
				{
					throw new InvalidRequestException("A question doesn't take a subtitle or a question.");
				}
			}
			case POINTED ->
			{
				if (draft.title() != null || draft.subtitle() != null || !draft.references().isEmpty())
				{
					throw new InvalidRequestException("A pointed paragraph takes only its question and body: its title is computed.");
				}
			}
		}
	}

	private static QuestionParagraph question(UserId sender, Conversation conversation, ParagraphDraft draft)
	{
		ParagraphId id = draft.question().orElseThrow(() -> new InvalidRequestException("A pointed paragraph needs the question it answers."));

		if (!(Paragraphs.visibleTo(sender, conversation, id) instanceof QuestionParagraph question))
		{
			throw new InvalidRequestException("Paragraph " + id.value() + " is not a question.");
		}

		return question;
	}
}
