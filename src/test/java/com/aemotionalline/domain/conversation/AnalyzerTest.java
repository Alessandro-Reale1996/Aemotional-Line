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

public class AnalyzerTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
	
	private static final DiscussionId FIRST_DISCUSSION = new DiscussionId(Ids.conversation(321L), 0);
	
	private static final ConstraintContext NOON = new ConstraintContext(LocalTime.NOON);
	
	private static Conversation startConversation()
	{
		Proposal proposal = new Proposal(Ids.proposal(1L), COUPLE.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(Ids.negotiation(60L), COUPLE, proposal, Clock.systemUTC());
		
		negotiation.acceptNegotiation(COUPLE.getPartnerTwoId());
		
		negotiation.sendProposal(COUPLE.getPartnerOneId(), "JUSTIFICATION TEXT");
		
		negotiation.acceptProposal(COUPLE.getPartnerTwoId());
		
		return Conversation.start(Ids.conversation(321L), COUPLE, negotiation);
	}
	
	private static void send(Conversation conversation, long messageId, UserId sender, Paragraph... paragraphs)
	{
		Message message = new Message(Ids.message(messageId), sender);
		
		for (Paragraph paragraph : paragraphs)
		{
			message.addParagraph(paragraph);
		}
		
		conversation.sendMessage(message, FIRST_DISCUSSION, NOON);
	}
	
	private static PointedParagraph answerTo(long id, QuestionParagraph question)
	{
		PointedParagraph answer = new PointedParagraph(Ids.paragraph(id), "In risposta a...", "Answer");
		
		answer.addReference(question);
		
		return answer;
	}
	
	// conversationNotAnsweredQuestions():
	
	@Test
	void shouldReportQuestionWithoutAnswer()
	{
		Conversation conversation = startConversation();
		
		QuestionParagraph question = new QuestionParagraph(Ids.paragraph(1L), "Why?", "Body");
		
		send(conversation, 1L, COUPLE.getPartnerOneId(), question);
		
		assertEquals(List.of(question), Analyzer.conversationNotAnsweredQuestions(conversation));
	}
	
	@Test
	void shouldNotReportQuestionAnsweredByPointedParagraph()
	{
		Conversation conversation = startConversation();
		
		QuestionParagraph question = new QuestionParagraph(Ids.paragraph(1L), "Why?", "Body");
		
		send(conversation, 1L, COUPLE.getPartnerOneId(), question);
		
		send(conversation, 2L, COUPLE.getPartnerTwoId(), answerTo(2L, question));
		
		assertTrue(Analyzer.conversationNotAnsweredQuestions(conversation).isEmpty());
	}
	
	@Test
	void shouldReportOnlyTheQuestionsLeftUnanswered()
	{
		Conversation conversation = startConversation();
		
		QuestionParagraph answered = new QuestionParagraph(Ids.paragraph(1L), "Why?", "Body");
		QuestionParagraph unanswered = new QuestionParagraph(Ids.paragraph(2L), "When?", "Body");
		
		send(conversation, 1L, COUPLE.getPartnerOneId(), answered, unanswered);
		
		send(conversation, 2L, COUPLE.getPartnerTwoId(), answerTo(3L, answered));
		
		assertEquals(List.of(unanswered), Analyzer.conversationNotAnsweredQuestions(conversation));
	}
	
	@Test
	void shouldReportQuestionThatOnlyReferencesAnEarlierParagraph()
	{
		Conversation conversation = startConversation();
		
		SimpleParagraph earlier = new SimpleParagraph(Ids.paragraph(1L), "Title", "Subtitle", "Body");
		
		send(conversation, 1L, COUPLE.getPartnerOneId(), earlier);
		
		// The question refers back to something said before, but nobody has answered it yet.
		QuestionParagraph question = new QuestionParagraph(Ids.paragraph(2L), "About this, why?", "Body");
		question.addReference(earlier);
		
		send(conversation, 2L, COUPLE.getPartnerTwoId(), question);
		
		assertEquals(List.of(question), Analyzer.conversationNotAnsweredQuestions(conversation));
	}
	
	// discussionNotAnsweredQuestion():
	
	@Test
	void shouldNotReportQuestionAnsweredInTheSameDiscussion()
	{
		Conversation conversation = startConversation();
		
		QuestionParagraph question = new QuestionParagraph(Ids.paragraph(1L), "Why?", "Body");
		
		send(conversation, 1L, COUPLE.getPartnerOneId(), question);
		
		send(conversation, 2L, COUPLE.getPartnerTwoId(), answerTo(2L, question));
		
		assertTrue(Analyzer.discussionNotAnsweredQuestion(conversation.findDiscussion(FIRST_DISCUSSION)).isEmpty());
	}
	
	@Test
	void shouldReportOnlyTheDiscussionQuestionsLeftUnanswered()
	{
		Conversation conversation = startConversation();
		
		QuestionParagraph answered = new QuestionParagraph(Ids.paragraph(1L), "Why?", "Body");
		QuestionParagraph unanswered = new QuestionParagraph(Ids.paragraph(2L), "When?", "Body");
		
		send(conversation, 1L, COUPLE.getPartnerOneId(), answered, unanswered);
		
		send(conversation, 2L, COUPLE.getPartnerTwoId(), answerTo(3L, answered));
		
		assertEquals(List.of(unanswered), Analyzer.discussionNotAnsweredQuestion(conversation.findDiscussion(FIRST_DISCUSSION)));
	}
}
