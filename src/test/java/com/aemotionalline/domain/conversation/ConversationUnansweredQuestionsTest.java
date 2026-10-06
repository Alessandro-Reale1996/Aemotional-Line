package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

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
import com.aemotionalline.domain.Ids;

/**
 * Section C, point 3 (spec 1.3.3): a reply may leave questions of the answered message unanswered.
 * The domain previews them (for the warning pop-up) and records them in the message when it is sent.
 */
public class ConversationUnansweredQuestionsTest
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

	private static SimpleParagraph simple(long id)
	{
		return new SimpleParagraph(Ids.paragraph(id), "Title", "Subtitle", "Body");
	}

	private static QuestionParagraph question(long id)
	{
		return new QuestionParagraph(Ids.paragraph(id), "Why?", "Body");
	}

	private static PointedParagraph answer(long id, QuestionParagraph question)
	{
		return new PointedParagraph(Ids.paragraph(id), question, "Answer");
	}

	// PREVIEW (for the warning pop-up):

	@Test
	void shouldPreviewTheQuestionsADraftLeavesUnansweredWithoutChangingAnything()
	{
		Conversation conversation = startConversation();
		QuestionParagraph answered = question(1L);
		QuestionParagraph skipped = question(2L);
		conversation.sendMessage(message(1L, A, answered, skipped), FIRST, NOON);

		Message draft = message(2L, B, answer(3L, answered));

		assertEquals(List.of(skipped), conversation.questionsLeftUnansweredBy(draft, FIRST));
		// Only a preview: nothing was sent, nothing was sealed.
		assertEquals(1, conversation.findDiscussion(FIRST).getMessages().size());
		assertEquals(2, conversation.getConversationGraph().getParagraphs().size());
		assertEquals(false, draft.isSealed());
	}

	@Test
	void shouldPreviewNothingWhenTheDraftAnswersEveryQuestion()
	{
		Conversation conversation = startConversation();
		QuestionParagraph question = question(1L);
		conversation.sendMessage(message(1L, A, question, simple(2L)), FIRST, NOON);

		assertTrue(conversation.questionsLeftUnansweredBy(message(2L, B, answer(3L, question)), FIRST).isEmpty());
	}

	@Test
	void shouldPreviewNothingForTheFirstMessageOfADiscussion()
	{
		Conversation conversation = startConversation();

		assertTrue(conversation.questionsLeftUnansweredBy(message(1L, A, question(1L)), FIRST).isEmpty());
	}

	// TRACKING ON SEND:

	@Test
	void shouldRecordTheSkippedQuestionsInTheSentMessage()
	{
		Conversation conversation = startConversation();
		QuestionParagraph answered = question(1L);
		QuestionParagraph skipped = question(2L);
		conversation.sendMessage(message(1L, A, answered, skipped), FIRST, NOON);

		Message reply = message(2L, B, answer(3L, answered));
		conversation.sendMessage(reply, FIRST, NOON);

		assertEquals(List.of(skipped.getId()), reply.getQuestionsLeftUnanswered());
	}

	@Test
	void shouldRecordNothingWhenEveryQuestionIsAnswered()
	{
		Conversation conversation = startConversation();
		QuestionParagraph question = question(1L);
		conversation.sendMessage(message(1L, A, question), FIRST, NOON);

		Message reply = message(2L, B, answer(2L, question));
		conversation.sendMessage(reply, FIRST, NOON);

		assertTrue(reply.getQuestionsLeftUnanswered().isEmpty());
	}

	@Test
	void shouldNotCountAQuestionAnsweredByADiscussionOpenedFromIt()
	{
		Conversation conversation = startConversation();
		QuestionParagraph question = question(1L);
		conversation.sendMessage(message(1L, A, question), FIRST, NOON);

		// B expands the question in a new discussion: that counts as answering it.
		conversation.openDiscussion(acceptedNegotiation(70L), question.getId());
		SimpleParagraph expansion = simple(2L);
		expansion.addReference(question);
		conversation.sendMessage(message(2L, B, expansion), SECOND, NOON);

		Message reply = message(3L, B, simple(3L));
		conversation.sendMessage(reply, FIRST, NOON);

		assertTrue(reply.getQuestionsLeftUnanswered().isEmpty());
	}

	// ANALYZER: skipped questions are not the same as questions still waiting for a reply.

	@Test
	void shouldListSkippedQuestionsButNotTheOnesStillWaiting()
	{
		Conversation conversation = startConversation();
		QuestionParagraph skipped = question(1L);
		conversation.sendMessage(message(1L, A, skipped), FIRST, NOON);

		QuestionParagraph waiting = question(2L);
		conversation.sendMessage(message(2L, B, waiting), FIRST, NOON);

		// B skipped A's question for good; A hasn't replied to B's question yet.
		assertEquals(List.of(skipped), Analyzer.questionsLeftUnanswered(conversation));
		assertEquals(List.of(skipped, waiting), Analyzer.conversationNotAnsweredQuestions(conversation));
	}
}
