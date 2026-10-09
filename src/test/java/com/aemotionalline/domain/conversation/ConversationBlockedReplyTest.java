package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.PointedParagraph;
import com.aemotionalline.domain.message.QuestionParagraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.user.UserId;

/**
 * Once a specific discussion is opened from a paragraph, that paragraph is answered only there (spec 1.3.3):
 * a reply to it in any other discussion is rejected, and a preview tells the frontend which discussions block a draft.
 */
public class ConversationBlockedReplyTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();

	private static final DiscussionId FIRST = new DiscussionId(Ids.conversation(321L), 0);
	private static final DiscussionId SECOND = new DiscussionId(Ids.conversation(321L), 1);

	private static final ConstraintContext NOON = new ConstraintContext(LocalTime.NOON);

	private static Negotiation settled(Negotiation negotiation)
	{
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION TEXT");
		negotiation.acceptProposal(B);

		return negotiation;
	}

	private static Negotiation discussionNegotiation(long id)
	{
		return settled(Negotiation.start(Ids.negotiation(id), COUPLE, new DiscussionProposal(Ids.proposal(id), A, "TITLE " + id, "SUBTITLE " + id), Clock.systemUTC()));
	}

	private static Message message(long id, UserId sender, Paragraph... paragraphs)
	{
		Message message = new Message(Ids.message(id), sender);

		for (Paragraph paragraph : paragraphs)
		{
			message.addParagraph(paragraph);
		}

		return message;
	}

	private static void send(Conversation conversation, DiscussionId discussion, Message message)
	{
		Reading.send(conversation, message, discussion, NOON);
	}

	private static SimpleParagraph simple(long id)
	{
		return new SimpleParagraph(Ids.paragraph(id), "Title", "Subtitle", "Body");
	}

	private static SimpleParagraph replyingTo(long id, Paragraph answered)
	{
		SimpleParagraph paragraph = simple(id);
		paragraph.addReference(answered);

		return paragraph;
	}

	// A's first message holds the paragraphs; the second discussion expands the first of them.
	private static Conversation conversationExpanding(Paragraph... paragraphs)
	{
		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE,
				settled(Negotiation.start(Ids.negotiation(60L), COUPLE, new AgreementProposal(Ids.proposal(60L), A, "TEXT"), Clock.systemUTC())));

		send(conversation, FIRST, message(1L, A, paragraphs));
		conversation.openDiscussion(discussionNegotiation(70L), paragraphs[0].getId());

		return conversation;
	}

	private static void assertNothingWasSent(Conversation conversation, int paragraphsInGraph)
	{
		assertEquals(1, conversation.findDiscussion(FIRST).getMessages().size());
		assertEquals(paragraphsInGraph, conversation.getConversationGraph().getParagraphs().size());
	}

	@Test
	void shouldNotAnswerTheOriginOfADiscussionOnTheMainConversation()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);

		assertThrows(DomainException.class, () -> send(conversation, FIRST, message(2L, B, replyingTo(2L, origin))));
		assertNothingWasSent(conversation, 1);
	}

	@Test
	void shouldBlockTheOriginAsSoonAsTheDiscussionIsOpened()
	{
		// The second discussion has no message yet: opening it is enough.
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);

		assertEquals(0, conversation.findDiscussion(SECOND).getMessages().size());
		assertThrows(DomainException.class, () -> send(conversation, FIRST, message(2L, B, replyingTo(2L, origin))));
	}

	@Test
	void shouldStillAnswerTheOtherParagraphsOfTheSameMessage()
	{
		SimpleParagraph origin = simple(1L);
		SimpleParagraph other = simple(2L);
		Conversation conversation = conversationExpanding(origin, other);

		assertDoesNotThrow(() -> send(conversation, FIRST, message(2L, B, replyingTo(3L, other))));
	}

	@Test
	void shouldRejectTheWholeMessageWhenOneParagraphAnswersTheOrigin()
	{
		SimpleParagraph origin = simple(1L);
		SimpleParagraph other = simple(2L);
		Conversation conversation = conversationExpanding(origin, other);

		assertThrows(DomainException.class,
				() -> send(conversation, FIRST, message(2L, B, replyingTo(3L, other), replyingTo(4L, origin))));
		assertNothingWasSent(conversation, 2);
	}

	@Test
	void shouldLetTheDiscussionItselfAnswerTheOrigin()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);

		assertDoesNotThrow(() -> send(conversation, SECOND, message(2L, B, replyingTo(2L, origin))));
	}

	@Test
	void shouldNotAnswerTheOriginFromTheSecondMessageOfItsDiscussionOnward()
	{
		// After the first message of the discussion, answers target the previous message, never the origin.
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);
		send(conversation, SECOND, message(2L, B, replyingTo(2L, origin)));

		assertThrows(DomainException.class, () -> send(conversation, SECOND, message(3L, A, replyingTo(3L, origin))));
	}

	@Test
	void shouldOpenOnlyOneDiscussionPerParagraph()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);
		Negotiation another = discussionNegotiation(80L);

		assertThrows(DomainException.class, () -> conversation.openDiscussion(another, origin.getId()));
		assertEquals(2, conversation.getDiscussions().size());

		// The rejected request didn't use up the negotiation.
		assertDoesNotThrow(() -> conversation.openDiscussion(another));
		assertEquals(3, conversation.getDiscussions().size());
	}

	@Test
	void shouldNotAnswerAQuestionThatHasADiscussionOnTheMainConversation()
	{
		QuestionParagraph question = new QuestionParagraph(Ids.paragraph(1L), "Why?", "Body");
		Conversation conversation = conversationExpanding(question);

		assertThrows(DomainException.class,
				() -> send(conversation, FIRST, message(2L, B, new PointedParagraph(Ids.paragraph(2L), question, "Answer"))));
		assertNothingWasSent(conversation, 1);
	}

	@Test
	void shouldAnswerAParagraphBeforeAnyDiscussionIsOpenedFromIt()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE,
				settled(Negotiation.start(Ids.negotiation(60L), COUPLE, new AgreementProposal(Ids.proposal(60L), A, "TEXT"), Clock.systemUTC())));
		send(conversation, FIRST, message(1L, A, origin));

		assertDoesNotThrow(() -> send(conversation, FIRST, message(2L, B, replyingTo(2L, origin))));
	}

	// PREVIEW:

	@Test
	void shouldPreviewTheDiscussionsThatBlockADraft()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);
		Message draft = message(2L, B, replyingTo(2L, origin));

		assertEquals(List.of(SECOND), conversation.discussionsBlockingReplyTo(draft, FIRST));
		assertNothingWasSent(conversation, 1);
	}

	@Test
	void shouldPreviewNothingForADraftThatExpandsTheOriginOrAnswersAnotherParagraph()
	{
		SimpleParagraph origin = simple(1L);
		SimpleParagraph other = simple(2L);
		Conversation conversation = conversationExpanding(origin, other);

		assertEquals(List.of(), conversation.discussionsBlockingReplyTo(message(2L, B, replyingTo(3L, origin)), SECOND));
		assertEquals(List.of(), conversation.discussionsBlockingReplyTo(message(3L, B, replyingTo(4L, other)), FIRST));
	}

	@Test
	void shouldNotPreviewANullDraft()
	{
		Conversation conversation = conversationExpanding(simple(1L));

		assertThrows(DomainException.class, () -> conversation.discussionsBlockingReplyTo(null, FIRST));
	}
}
