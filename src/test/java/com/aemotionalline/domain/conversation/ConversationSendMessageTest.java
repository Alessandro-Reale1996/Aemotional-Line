package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.TimeConstraint;
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
 * The rules Conversation.sendMessage enforces before accepting a message,
 * and the guarantee that a rejected message changes nothing.
 */
public class ConversationSendMessageTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();

	private static final DiscussionId FIRST_DISCUSSION = new DiscussionId(Ids.conversation(321L), 0);

	private static final ConstraintContext NOON = new ConstraintContext(LocalTime.NOON);

	private static Conversation startConversation()
	{
		Proposal proposal = new Proposal(Ids.proposal(1L), A, "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(60L), COUPLE, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(B);

		negotiation.sendProposal(A, "JUSTIFICATION TEXT");

		negotiation.acceptProposal(B);

		return Conversation.start(Ids.conversation(321L), COUPLE, negotiation);
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

	private static void send(Conversation conversation, Message message)
	{
		conversation.sendMessage(message, FIRST_DISCUSSION, NOON);
	}

	private static SimpleParagraph simple(long id)
	{
		return new SimpleParagraph(Ids.paragraph(id), "Title", "Subtitle", "Body");
	}

	// A rejected message must leave the discussion and the graph exactly as they were.
	private static void assertUnchanged(Conversation conversation, int messages, int paragraphs, UserId lastSender)
	{
		Discussion discussion = conversation.findDiscussion(FIRST_DISCUSSION);

		assertEquals(messages, discussion.getMessages().size());
		assertEquals(paragraphs, conversation.getConversationGraph().getParagraphs().size());
		assertEquals(lastSender, discussion.getLastSender());
	}

	@Test
	void shouldAcceptMessagesWithNewParagraphs()
	{
		Conversation conversation = startConversation();

		send(conversation, message(1L, A, simple(1L)));

		assertDoesNotThrow(() -> send(conversation, message(2L, B, simple(2L), simple(3L))));
		assertUnchanged(conversation, 2, 3, B);
	}

	// PARAGRAPH IDS (5b):

	@Test
	void shouldRejectTheSameParagraphSentAgain()
	{
		Conversation conversation = startConversation();
		SimpleParagraph paragraph = simple(1L);
		send(conversation, message(1L, A, paragraph));

		assertThrows(DomainException.class, () -> send(conversation, message(2L, B, paragraph)));
		assertUnchanged(conversation, 1, 1, A);
	}

	@Test
	void shouldRejectANewParagraphReusingTheIdOfOneAlreadySent()
	{
		Conversation conversation = startConversation();
		send(conversation, message(1L, A, simple(1L)));

		// A different object, but the id already identifies a paragraph of the conversation.
		assertThrows(DomainException.class, () -> send(conversation, message(2L, B, simple(1L))));
		assertUnchanged(conversation, 1, 1, A);
	}

	@Test
	void shouldRejectTheSameIdTwiceInOneMessage()
	{
		Conversation conversation = startConversation();

		assertThrows(DomainException.class, () -> send(conversation, message(1L, A, simple(1L), simple(1L))));
		assertUnchanged(conversation, 0, 0, null);
	}

	// CHECKS BEFORE CHANGES (5a):

	@Test
	void shouldLeaveTheDiscussionUnchangedWhenTheTurnIsWrong()
	{
		Conversation conversation = startConversation();
		send(conversation, message(1L, A, simple(1L)));

		assertThrows(DomainException.class, () -> send(conversation, message(2L, A, simple(2L))));
		assertUnchanged(conversation, 1, 1, A);
	}
	
	// REFERENCES ONLY TO THE MESSAGE BEING ANSWERED (5c):
	
	private static Negotiation acceptedNegotiation(long id)
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(id), COUPLE, new Proposal(Ids.proposal(id), A, "TEXT"), Clock.systemUTC());
		
		negotiation.acceptNegotiation(B);
		
		negotiation.sendProposal(A, "JUSTIFICATION TEXT");
		
		negotiation.acceptProposal(B);
		
		return negotiation;
	}
	
	@Test
	void shouldAcceptReferencesToTheMessageBeingAnswered()
	{
		Conversation conversation = startConversation();
		SimpleParagraph answered = simple(1L);
		send(conversation, message(1L, A, answered));
		
		// Several paragraphs of the same reply may refer to the same paragraph (rule 3).
		SimpleParagraph firstReply = simple(2L);
		SimpleParagraph secondReply = simple(3L);
		firstReply.addReference(answered);
		secondReply.addReference(answered);
		
		assertDoesNotThrow(() -> send(conversation, message(2L, B, firstReply, secondReply)));
		assertUnchanged(conversation, 2, 3, B);
	}
	
	@Test
	void shouldRejectAReferenceToAParagraphNeverSent()
	{
		Conversation conversation = startConversation();
		send(conversation, message(1L, A, simple(1L)));
		
		SimpleParagraph reply = simple(2L);
		reply.addReference(simple(99L));
		
		assertThrows(DomainException.class, () -> send(conversation, message(2L, B, reply)));
		assertUnchanged(conversation, 1, 1, A);
	}
	
	@Test
	void shouldRejectAReferenceInTheFirstMessageOfADiscussion()
	{
		Conversation conversation = startConversation();
		SimpleParagraph inFirstDiscussion = simple(1L);
		send(conversation, message(1L, A, inFirstDiscussion));
		
		conversation.openDiscussion(acceptedNegotiation(70L));
		DiscussionId secondDiscussion = new DiscussionId(Ids.conversation(321L), 1);
		
		// The new discussion has no message to answer, and the target belongs to another discussion.
		SimpleParagraph reply = simple(2L);
		reply.addReference(inFirstDiscussion);
		
		assertThrows(DomainException.class, () -> conversation.sendMessage(message(2L, B, reply), secondDiscussion, NOON));
		assertEquals(0, conversation.findDiscussion(secondDiscussion).getMessages().size());
		assertEquals(1, conversation.getConversationGraph().getParagraphs().size());
	}
	
	@Test
	void shouldRejectAReferenceToAnOlderMessage()
	{
		Conversation conversation = startConversation();
		SimpleParagraph old = simple(1L);
		send(conversation, message(1L, A, old));
		send(conversation, message(2L, B, simple(2L)));
		send(conversation, message(3L, A, simple(3L)));
		
		// B is answering A's third message, not the first one.
		SimpleParagraph reply = simple(4L);
		reply.addReference(old);
		
		assertThrows(DomainException.class, () -> send(conversation, message(4L, B, reply)));
		assertUnchanged(conversation, 3, 3, A);
	}
	
	@Test
	void shouldRejectAReferenceToTheSendersOwnParagraph()
	{
		Conversation conversation = startConversation();
		SimpleParagraph own = simple(1L);
		send(conversation, message(1L, A, own));
		send(conversation, message(2L, B, simple(2L)));
		
		// Pointing back to one's own paragraph is a self-citation, not a reference.
		SimpleParagraph reply = simple(3L);
		reply.addReference(own);
		
		assertThrows(DomainException.class, () -> send(conversation, message(3L, A, reply)));
		assertUnchanged(conversation, 2, 2, B);
	}
	
	@Test
	void shouldRejectAReferenceToAParagraphOfTheSameMessage()
	{
		Conversation conversation = startConversation();
		send(conversation, message(1L, A, simple(1L)));
		
		SimpleParagraph first = simple(2L);
		SimpleParagraph second = simple(3L);
		second.addReference(first);
		
		assertThrows(DomainException.class, () -> send(conversation, message(2L, B, first, second)));
		assertUnchanged(conversation, 1, 1, A);
	}
	
	// POINTED ANSWERS (5d): a question gets at most one answer, and a pointed paragraph must answer one.
	
	private static QuestionParagraph question(long id)
	{
		return new QuestionParagraph(Ids.paragraph(id), "Why?", "Body");
	}
	
	private static PointedParagraph answer(long id, QuestionParagraph question)
	{
		return new PointedParagraph(Ids.paragraph(id), question, "Answer");
	}
	
	@Test
	void shouldAcceptOneAnswerForEachQuestion()
	{
		Conversation conversation = startConversation();
		QuestionParagraph first = question(1L);
		QuestionParagraph second = question(2L);
		send(conversation, message(1L, A, first, second));
		
		assertDoesNotThrow(() -> send(conversation, message(2L, B, answer(3L, first), answer(4L, second))));
		assertUnchanged(conversation, 2, 4, B);
	}
	
	@Test
	void shouldRejectTwoAnswersToTheSameQuestion()
	{
		Conversation conversation = startConversation();
		QuestionParagraph question = question(1L);
		send(conversation, message(1L, A, question));
		
		assertThrows(DomainException.class, () -> send(conversation, message(2L, B, answer(2L, question), answer(3L, question))));
		assertUnchanged(conversation, 1, 1, A);
	}
	
	@Test
	void shouldNotBuildAPointedParagraphWithoutAQuestion()
	{
		// Since section C point 2 the question is a constructor argument: the rule holds before any send.
		assertThrows(DomainException.class, () -> answer(2L, null));
	}
	
	@Test
	void shouldRejectAPointedParagraphInTheFirstMessage()
	{
		Conversation conversation = startConversation();
		
		// The first message answers nothing, so whatever question a pointed paragraph answers, it is out of reach.
		QuestionParagraph neverSent = question(9L);
		
		assertThrows(DomainException.class, () -> send(conversation, message(1L, A, answer(1L, neverSent))));
		assertUnchanged(conversation, 0, 0, null);
	}

	// SEALED AFTER SENDING (5e):
	
	@Test
	void shouldSealTheMessageOnceSent()
	{
		Conversation conversation = startConversation();
		Message sent = message(1L, A, simple(1L));
		send(conversation, sent);
		
		assertThrows(DomainException.class, () -> sent.addParagraph(simple(2L)));
		assertEquals(1, conversation.findDiscussion(FIRST_DISCUSSION).getMessages().get(0).getParagraphs().size());
		assertUnchanged(conversation, 1, 1, A);
	}
	
	@Test
	void shouldNotLetASentParagraphCloseACycle()
	{
		Conversation conversation = startConversation();
		SimpleParagraph first = simple(1L);
		send(conversation, message(1L, A, first));
		
		SimpleParagraph reply = simple(2L);
		reply.addReference(first);
		send(conversation, message(2L, B, reply));
		
		// first -> reply would close the cycle first <-> reply: impossible once first is sealed.
		assertThrows(DomainException.class, () -> first.addReference(reply));
		assertTrue(first.getReferences().isEmpty());
	}
	
	@Test
	void shouldRejectSendingTheSameMessageTwice()
	{
		Conversation conversation = startConversation();
		Message sent = message(1L, A, simple(1L));
		send(conversation, sent);
		send(conversation, message(2L, B, simple(2L)));
		
		assertThrows(DomainException.class, () -> send(conversation, sent));
		assertUnchanged(conversation, 2, 2, B);
	}

	// CONSTRAINT CONTEXT (5f, A12):
	
	@Test
	void shouldSendWithoutContextWhenTheSenderHasNoConstraints()
	{
		Conversation conversation = startConversation();
		
		assertDoesNotThrow(() -> conversation.sendMessage(message(1L, A, simple(1L)), FIRST_DISCUSSION, null));
		assertUnchanged(conversation, 1, 1, A);
	}
	
	@Test
	void shouldRequireContextWhenTheSenderHasConstraints()
	{
		Conversation conversation = startConversation();
		
		ConstraintChangeRequest request = new ConstraintChangeRequest(COUPLE.getTherapistId(),
				List.of(new ConstraintAssignment(A, new TimeConstraint(LocalTime.of(9, 0)))), COUPLE);
		request.approve(A);
		request.approve(B);
		conversation.applyConstraints(request);
		
		assertThrows(DomainException.class, () -> conversation.sendMessage(message(1L, A, simple(1L)), FIRST_DISCUSSION, null));
		assertUnchanged(conversation, 0, 0, null);
	}
	
	// CONSTRAINT CHANGE REQUESTS (6d):
	
	private static ConstraintChangeRequest approvedRequest(Couple couple)
	{
		ConstraintChangeRequest request = new ConstraintChangeRequest(couple.getTherapistId(),
				List.of(new ConstraintAssignment(couple.getPartnerOneId(), new TimeConstraint(LocalTime.of(9, 0)))), couple);
		request.approve(couple.getPartnerOneId());
		request.approve(couple.getPartnerTwoId());
		return request;
	}
	
	@Test
	void shouldNotApplyARequestOfAnotherCouple()
	{
		Conversation conversation = startConversation();
		Couple other = new Couple(Ids.couple(2L), Ids.user(11L), Ids.user(21L), Ids.user(31L));
		
		assertThrows(DomainException.class, () -> conversation.applyConstraints(approvedRequest(other)));
		assertTrue(conversation.getConstraintSet().isEmpty());
	}
	
	@Test
	void shouldNotApplyTheSameRequestTwice()
	{
		Conversation conversation = startConversation();
		ConstraintChangeRequest request = approvedRequest(COUPLE);
		
		conversation.applyConstraints(request);
		
		assertThrows(DomainException.class, () -> conversation.applyConstraints(request));
		assertEquals(1, conversation.getConstraintSet().getAssignments().size());
	}
	
	@Test
	void shouldNotApplyANullRequest()
	{
		assertThrows(DomainException.class, () -> startConversation().applyConstraints(null));
	}
	
	// SELF-CITATIONS (5g):
	
	@Test
	void shouldAcceptASelfCitationOfTheSendersOwnEarlierParagraph()
	{
		Conversation conversation = startConversation();
		SimpleParagraph own = simple(1L);
		send(conversation, message(1L, A, own));
		
		// One topic: every reply answers the previous message, so paragraph 1 stays in the topic of the last one.
		SimpleParagraph second = simple(2L);
		second.addReference(own);
		send(conversation, message(2L, B, second));
		
		SimpleParagraph third = simple(3L);
		third.addReference(second);
		send(conversation, message(3L, A, third));
		
		SimpleParagraph fourth = simple(4L);
		fourth.addReference(third);
		send(conversation, message(4L, B, fourth));
		
		// Unlike a reference, a self-citation may point to an older message of the same topic.
		SimpleParagraph citing = simple(5L);
		citing.addReference(fourth);
		citing.addSelfCitation(own);
		
		assertDoesNotThrow(() -> send(conversation, message(5L, A, citing)));
		assertUnchanged(conversation, 5, 5, A);
	}
	
	@Test
	void shouldRejectASelfCitationOfTheOtherPartnersParagraph()
	{
		Conversation conversation = startConversation();
		SimpleParagraph othersParagraph = simple(1L);
		send(conversation, message(1L, A, othersParagraph));
		
		SimpleParagraph citing = simple(2L);
		citing.addSelfCitation(othersParagraph);
		
		assertThrows(DomainException.class, () -> send(conversation, message(2L, B, citing)));
		assertUnchanged(conversation, 1, 1, A);
	}
	
	@Test
	void shouldRejectASelfCitationOfAParagraphNeverSent()
	{
		Conversation conversation = startConversation();
		
		SimpleParagraph citing = simple(2L);
		citing.addSelfCitation(simple(99L));
		
		assertThrows(DomainException.class, () -> send(conversation, message(1L, A, citing)));
		assertUnchanged(conversation, 0, 0, null);
	}
	
	@Test
	void shouldLeaveSelfCitationsOutOfTheConversationRebuild()
	{
		Conversation conversation = startConversation();
		
		// One topic, with a question that nobody answers.
		SimpleParagraph root = simple(1L);
		send(conversation, message(1L, A, root));
		
		SimpleParagraph reply = simple(2L);
		reply.addReference(root);
		send(conversation, message(2L, B, reply));
		
		QuestionParagraph ownQuestion = question(3L);
		ownQuestion.addReference(reply);
		SimpleParagraph plain = simple(4L);
		plain.addReference(reply);
		send(conversation, message(3L, A, ownQuestion, plain));
		
		SimpleParagraph next = simple(5L);
		next.addReference(plain);
		send(conversation, message(4L, B, next));
		
		SimpleParagraph citing = simple(6L);
		citing.addReference(next);
		citing.addSelfCitation(ownQuestion);
		send(conversation, message(5L, A, citing));
		
		ConversationGraph graph = conversation.getConversationGraph();
		
		// The self-citation answers nothing and adds no link: the topic is rebuilt from the references only.
		assertTrue(graph.findRepliesTo(ownQuestion).isEmpty());
		assertEquals(List.of(root), graph.findRootsOf(citing));
		assertEquals(List.of(ownQuestion), Analyzer.conversationNotAnsweredQuestions(conversation));
	}
	
	@Test
	void shouldRejectASelfCitationOutsideTheTopicOfTheCitingParagraph()
	{
		Conversation conversation = startConversation();
		SimpleParagraph firstTopic = simple(1L);
		SimpleParagraph secondTopic = simple(2L);
		send(conversation, message(1L, A, firstTopic, secondTopic));
		
		SimpleParagraph reply = simple(3L);
		reply.addReference(secondTopic);
		send(conversation, message(2L, B, reply));
		
		// The reply is about the second topic: the first one is another subject, even if the same user wrote it.
		SimpleParagraph citing = simple(4L);
		citing.addReference(reply);
		citing.addSelfCitation(firstTopic);
		
		assertThrows(DomainException.class, () -> send(conversation, message(3L, A, citing)));
		assertUnchanged(conversation, 2, 3, B);
	}
	
	@Test
	void shouldRejectASelfCitationInAParagraphThatAnswersNothing()
	{
		Conversation conversation = startConversation();
		SimpleParagraph own = simple(1L);
		send(conversation, message(1L, A, own));
		send(conversation, message(2L, B, simple(2L)));
		
		// Without references the paragraph has no topic to cite from.
		SimpleParagraph citing = simple(3L);
		citing.addSelfCitation(own);
		
		assertThrows(DomainException.class, () -> send(conversation, message(3L, A, citing)));
		assertUnchanged(conversation, 2, 2, B);
	}
	
	@Test
	void shouldAllowCitingBothTopicsWhenAReplyJoinsThem()
	{
		Conversation conversation = startConversation();
		SimpleParagraph firstTopic = simple(1L);
		SimpleParagraph secondTopic = simple(2L);
		send(conversation, message(1L, A, firstTopic, secondTopic));
		
		SimpleParagraph join = simple(3L);
		join.addReference(firstTopic);
		join.addReference(secondTopic);
		send(conversation, message(2L, B, join));
		
		SimpleParagraph citing = simple(4L);
		citing.addReference(join);
		citing.addSelfCitation(firstTopic);
		citing.addSelfCitation(secondTopic);
		
		assertDoesNotThrow(() -> send(conversation, message(3L, A, citing)));
		assertUnchanged(conversation, 3, 4, A);
	}
}
