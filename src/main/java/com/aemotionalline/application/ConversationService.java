package com.aemotionalline.application;

import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.view.ConversationView;
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
	@Transactional(readOnly = true)
	public ConversationView view(UserId viewer, ConversationId id)
	{
		Conversation conversation = conversations.findById(id).orElseThrow(() -> new NotFoundException("Conversation not found."));
		Access.ensureMember(conversation.getCouple(), viewer, "Conversation");

		return Views.conversation(conversation, viewer);
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

	/** For the "you are leaving questions unanswered" pop-up: the questions of the message being answered the draft skips. */
	@Transactional(readOnly = true)
	public List<ParagraphId> previewQuestionsLeftUnanswered(UserId sender, ConversationId id, int discussionNumber, MessageDraft draft)
	{
		Conversation conversation = readable(sender, id);

		return conversation.questionsLeftUnansweredBy(build(sender, conversation, draft), new DiscussionId(id, discussionNumber)).stream()
				.map(Paragraph::getId)
				.toList();
	}

	/** For the "this paragraph has a specific discussion" pop-up: the discussions that keep the draft from being sent here. */
	@Transactional(readOnly = true)
	public List<DiscussionId> previewBlockingDiscussions(UserId sender, ConversationId id, int discussionNumber, MessageDraft draft)
	{
		Conversation conversation = readable(sender, id);

		return conversation.discussionsBlockingReplyTo(build(sender, conversation, draft), new DiscussionId(id, discussionNumber));
	}

	private Conversation locked(UserId user, ConversationId id)
	{
		Conversation conversation = conversations.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Conversation not found."));
		Access.ensureMember(conversation.getCouple(), user, "Conversation");

		return conversation;
	}

	private Conversation readable(UserId user, ConversationId id)
	{
		Conversation conversation = conversations.findById(id).orElseThrow(() -> new NotFoundException("Conversation not found."));
		Access.ensureMember(conversation.getCouple(), user, "Conversation");

		return conversation;
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
			if (paragraphDraft == null || paragraphDraft.type() == null)
			{
				throw new InvalidRequestException("Every paragraph of the draft needs a type.");
			}

			Paragraph paragraph = switch (paragraphDraft.type())
			{
				case SIMPLE -> new SimpleParagraph(ParagraphId.random(), paragraphDraft.title(), paragraphDraft.subtitle(), paragraphDraft.body());
				case QUESTION -> new QuestionParagraph(ParagraphId.random(), paragraphDraft.title(), paragraphDraft.body());
				case POINTED -> new PointedParagraph(ParagraphId.random(), question(sender, conversation, paragraphDraft), paragraphDraft.body());
			};

			if (paragraph instanceof PointedParagraph)
			{
				if (!paragraphDraft.references().isEmpty())
				{
					throw new InvalidRequestException("A pointed paragraph refers only to the question it answers.");
				}
			}
			else
			{
				paragraphDraft.references().forEach(reference -> paragraph.addReference(Paragraphs.visibleTo(sender, conversation, reference)));
			}

			paragraphDraft.selfCitations().forEach(cited -> paragraph.addSelfCitation(Paragraphs.visibleTo(sender, conversation, cited)));

			message.addParagraph(paragraph);
		}

		return message;
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
