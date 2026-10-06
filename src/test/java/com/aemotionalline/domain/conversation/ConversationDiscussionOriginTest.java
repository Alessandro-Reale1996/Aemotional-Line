package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

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
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;

/**
 * A discussion can be opened to expand a topic exposed in a paragraph. The first message of such a discussion
 * answers that paragraph, so topics are not confined to one discussion: they continue through the graph.
 */
public class ConversationDiscussionOriginTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();

	private static final DiscussionId FIRST = new DiscussionId(Ids.conversation(321L), 0);
	private static final DiscussionId SECOND = new DiscussionId(Ids.conversation(321L), 1);

	private static final ConstraintContext NOON = new ConstraintContext(LocalTime.NOON);

	private static Negotiation acceptedNegotiation(long id)
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(id), COUPLE, new Proposal(Ids.proposal(id), A, "TEXT"), Clock.systemUTC());

		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION TEXT");
		negotiation.acceptProposal(B);

		return negotiation;
	}

	private static Conversation startConversation()
	{
		return Conversation.start(Ids.conversation(321L), COUPLE, acceptedNegotiation(60L));
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
		conversation.sendMessage(message, discussion, NOON);
	}

	private static SimpleParagraph simple(long id)
	{
		return new SimpleParagraph(Ids.paragraph(id), "Title", "Subtitle", "Body");
	}

	private static SimpleParagraph simpleReplyingTo(long id, Paragraph... answered)
	{
		SimpleParagraph paragraph = simple(id);

		for (Paragraph reference : answered)
		{
			paragraph.addReference(reference);
		}

		return paragraph;
	}

	private static QuestionParagraph question(long id)
	{
		return new QuestionParagraph(Ids.paragraph(id), "Why?", "Body");
	}

	private static PointedParagraph answer(long id, QuestionParagraph question)
	{
		return new PointedParagraph(Ids.paragraph(id), question, "Answer");
	}

	// The first discussion holds the origin, sent by A; the second one expands it.
	private static Conversation conversationExpanding(Paragraph origin)
	{
		Conversation conversation = startConversation();

		send(conversation, FIRST, message(1L, A, origin));
		conversation.openDiscussion(acceptedNegotiation(70L), origin.getId());

		return conversation;
	}

	private static void assertSecondDiscussionEmpty(Conversation conversation, int paragraphsInGraph)
	{
		assertEquals(0, conversation.findDiscussion(SECOND).getMessages().size());
		assertEquals(paragraphsInGraph, conversation.getConversationGraph().getParagraphs().size());
	}

	// THE ORIGIN:

	@Test
	void shouldRememberTheParagraphADiscussionExpands()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);

		assertEquals(Optional.of(origin.getId()), conversation.findDiscussion(SECOND).getOriginParagraphId());
		assertEquals(Optional.empty(), conversation.findDiscussion(FIRST).getOriginParagraphId());
	}

	@Test
	void shouldNotOpenADiscussionFromAParagraphThatWasNotSent()
	{
		Conversation conversation = startConversation();
		Negotiation negotiation = acceptedNegotiation(70L);

		assertThrows(DomainException.class, () -> conversation.openDiscussion(negotiation, Ids.paragraph(99L)));
		assertEquals(1, conversation.getDiscussions().size());

		// The rejected request didn't use up the negotiation.
		assertDoesNotThrow(() -> conversation.openDiscussion(negotiation));
		assertEquals(2, conversation.getDiscussions().size());
	}

	@Test
	void shouldNotOpenADiscussionFromANullParagraph()
	{
		Conversation conversation = startConversation();

		assertThrows(DomainException.class, () -> conversation.openDiscussion(acceptedNegotiation(70L), null));
		assertEquals(1, conversation.getDiscussions().size());
	}

	// THE FIRST MESSAGE OF A DISCUSSION WITH AN ORIGIN:

	@Test
	void shouldAcceptAFirstMessageWhoseParagraphsAllReferToTheOrigin()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);

		assertDoesNotThrow(() -> send(conversation, SECOND,
				message(2L, B, simpleReplyingTo(2L, origin), simpleReplyingTo(3L, origin))));
		assertEquals(1, conversation.findDiscussion(SECOND).getMessages().size());
		assertEquals(3, conversation.getConversationGraph().getParagraphs().size());
		assertEquals(2, conversation.getConversationGraph().findRepliesTo(origin).size());
	}

	@Test
	void shouldRequireEveryParagraphOfTheFirstMessageToReferToTheOrigin()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);

		assertThrows(DomainException.class, () -> send(conversation, SECOND,
				message(2L, B, simpleReplyingTo(2L, origin), simple(3L))));
		assertSecondDiscussionEmpty(conversation, 1);
	}

	@Test
	void shouldNotAllowAFirstMessageToReferToAnyOtherParagraph()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = startConversation();
		SimpleParagraph other = simple(2L);
		send(conversation, FIRST, message(1L, A, origin));
		send(conversation, FIRST, message(2L, B, other));
		conversation.openDiscussion(acceptedNegotiation(70L), origin.getId());

		assertThrows(DomainException.class, () -> send(conversation, SECOND,
				message(3L, A, simpleReplyingTo(3L, origin, other))));
		assertSecondDiscussionEmpty(conversation, 2);
	}

	@Test
	void shouldKeepRejectingReferencesInTheFirstMessageOfADiscussionWithoutOrigin()
	{
		Conversation conversation = startConversation();
		SimpleParagraph inFirstDiscussion = simple(1L);
		send(conversation, FIRST, message(1L, A, inFirstDiscussion));
		conversation.openDiscussion(acceptedNegotiation(70L));

		assertThrows(DomainException.class, () -> send(conversation, SECOND,
				message(2L, B, simpleReplyingTo(2L, inFirstDiscussion))));
		assertSecondDiscussionEmpty(conversation, 1);
	}

	// WHEN THE ORIGIN IS A QUESTION:

	@Test
	void shouldLetAnyParagraphExpandAQuestionInTheFirstMessage()
	{
		QuestionParagraph origin = question(1L);
		Conversation conversation = conversationExpanding(origin);

		assertDoesNotThrow(() -> send(conversation, SECOND, message(2L, B, simpleReplyingTo(2L, origin))));
		assertEquals(1, conversation.findDiscussion(SECOND).getMessages().size());
	}

	@Test
	void shouldNotLetASimpleParagraphAnswerAQuestionInAnOrdinaryMessage()
	{
		Conversation conversation = startConversation();
		QuestionParagraph question = question(1L);
		send(conversation, FIRST, message(1L, A, question));

		// Only a pointed paragraph answers a question, outside the first message of the discussion that expands it.
		assertThrows(DomainException.class, () -> send(conversation, FIRST, message(2L, B, simpleReplyingTo(2L, question))));
		assertEquals(1, conversation.findDiscussion(FIRST).getMessages().size());
	}

	@Test
	void shouldNotLetASimpleParagraphAnswerAQuestionThatIsNotTheOrigin()
	{
		Conversation conversation = startConversation();
		SimpleParagraph origin = simple(1L);
		QuestionParagraph other = question(2L);
		send(conversation, FIRST, message(1L, A, origin));
		send(conversation, FIRST, message(2L, B, other));
		conversation.openDiscussion(acceptedNegotiation(70L), origin.getId());

		// The question was not the paragraph expanded: that reference is invalid anyway, in any message.
		assertThrows(DomainException.class, () -> send(conversation, FIRST, message(3L, A, simpleReplyingTo(3L, other))));
		assertEquals(2, conversation.findDiscussion(FIRST).getMessages().size());
	}

	@Test
	void shouldAllowAPointedParagraphInTheFirstMessageWhenTheOriginIsAQuestion()
	{
		QuestionParagraph origin = question(1L);
		Conversation conversation = conversationExpanding(origin);

		assertDoesNotThrow(() -> send(conversation, SECOND, message(2L, B, answer(2L, origin))));
	}

	@Test
	void shouldRejectAPointedParagraphInTheFirstMessageWhenTheOriginIsNotAQuestion()
	{
		SimpleParagraph origin = simple(1L);
		Conversation conversation = conversationExpanding(origin);
		// A pointed paragraph can only answer a question, and this discussion expands a simple paragraph:
		// whatever question it answers is not the origin, so the first message rejects it.
		PointedParagraph pointed = new PointedParagraph(Ids.paragraph(3L), new QuestionParagraph(Ids.paragraph(9L), "Why?", "Body"), "Answer");

		assertThrows(DomainException.class, () -> send(conversation, SECOND,
				message(2L, B, simpleReplyingTo(2L, origin), pointed)));
		assertSecondDiscussionEmpty(conversation, 1);
	}

	@Test
	void shouldRejectTwoPointedAnswersToTheOriginQuestion()
	{
		QuestionParagraph origin = question(1L);
		Conversation conversation = conversationExpanding(origin);

		assertThrows(DomainException.class, () -> send(conversation, SECOND,
				message(2L, B, answer(2L, origin), answer(3L, origin))));
		assertSecondDiscussionEmpty(conversation, 1);
	}

	// A QUESTION EXPANDED BY A DISCUSSION COUNTS AS ANSWERED:

	@Test
	void shouldNotReportAQuestionOnceItsDiscussionHasAMessage()
	{
		QuestionParagraph origin = question(1L);
		Conversation conversation = conversationExpanding(origin);

		send(conversation, SECOND, message(2L, B, simpleReplyingTo(2L, origin)));

		assertTrue(Analyzer.conversationNotAnsweredQuestions(conversation).isEmpty());
		assertTrue(Analyzer.discussionNotAnsweredQuestion(conversation, conversation.findDiscussion(FIRST)).isEmpty());
	}

	@Test
	void shouldStillReportAQuestionWhoseDiscussionHasNoMessageYet()
	{
		QuestionParagraph origin = question(1L);
		Conversation conversation = conversationExpanding(origin);

		assertEquals(List.of(origin), Analyzer.conversationNotAnsweredQuestions(conversation));
		assertEquals(List.of(origin), Analyzer.discussionNotAnsweredQuestion(conversation, conversation.findDiscussion(FIRST)));
	}

	// SELF-CITATIONS IN A DISCUSSION WITH AN ORIGIN: the topic goes on across discussions.

	@Test
	void shouldLetTheFirstMessageCiteAnEarlierParagraphOfTheTopicFromAnotherDiscussion()
	{
		Conversation conversation = startConversation();
		SimpleParagraph earlier = simple(1L);
		SimpleParagraph origin = simpleReplyingTo(2L, earlier);
		send(conversation, FIRST, message(1L, A, earlier));
		send(conversation, FIRST, message(2L, B, origin));
		conversation.openDiscussion(acceptedNegotiation(70L), origin.getId());

		SimpleParagraph citing = simpleReplyingTo(3L, origin);
		citing.addSelfCitation(earlier);

		assertDoesNotThrow(() -> send(conversation, SECOND, message(3L, A, citing)));
	}

	@Test
	void shouldNotLetTheFirstMessageCiteAParagraphOfAnotherTopic()
	{
		Conversation conversation = startConversation();
		SimpleParagraph earlier = simple(1L);
		SimpleParagraph origin = simpleReplyingTo(2L, earlier);
		SimpleParagraph otherTopic = simple(3L);
		send(conversation, FIRST, message(1L, A, earlier));
		send(conversation, FIRST, message(2L, B, origin));
		send(conversation, FIRST, message(3L, A, otherTopic));
		conversation.openDiscussion(acceptedNegotiation(70L), origin.getId());

		SimpleParagraph citing = simpleReplyingTo(4L, origin);
		citing.addSelfCitation(otherTopic);

		assertThrows(DomainException.class, () -> send(conversation, SECOND, message(4L, A, citing)));
		assertSecondDiscussionEmpty(conversation, 3);
	}
}
