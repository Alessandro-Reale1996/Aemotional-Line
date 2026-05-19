package com.aemotionalline.domain.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.conversation.ConversationGraph;

public class QuestionTrackerTest
{
	@Test
	void shouldSuccessfullyFindUnansweredQuestions()
	{
		QuestionParagraph asweredQuestion = 
				new QuestionParagraph(new ParagraphId(10L), ParagraphType.QUESTION, "Title?", "Subtitle?", "Question?");
		

	    QuestionParagraph unansweredQuestion =
	        new QuestionParagraph(new ParagraphId(1L), ParagraphType.QUESTION, "Title?", "Subtitle?", "Why did this happen?");

	    SimpleParagraph answer =
	        new SimpleParagraph(new ParagraphId(2L), ParagraphType.SIMPLE, "Answer", "Subtitle", "Because I misunderstood.");

	    answer.addReference(new ParagraphReference(asweredQuestion.getId()));
		
		ConversationGraph graph = new ConversationGraph();
		
		  graph.addParagraph(asweredQuestion);
		  graph.addParagraph(answer);
		  graph.addParagraph(unansweredQuestion);

		    QuestionTracker tracker = new QuestionTracker(graph);

		    List<QuestionParagraph> unanswered = tracker.findUnansweredQuestions();

		    assertEquals(1, unanswered.size());
		    assertEquals(unansweredQuestion.getId(), unanswered.get(0).getId());
		
	}
	
	@Test
	void shouldConsiderQuestionAnsweredWhenTheCase()
	{
		QuestionParagraph answeredQuestion = 
				new QuestionParagraph(new ParagraphId(10L), ParagraphType.QUESTION, "Title?", "Subtitle?", "Question?");
		

	    QuestionParagraph unansweredQuestion =
	        new QuestionParagraph(new ParagraphId(1L), ParagraphType.QUESTION, "Title?", "Subtitle?", "Why did this happen?");

	    SimpleParagraph answer =
	        new SimpleParagraph(new ParagraphId(2L), ParagraphType.SIMPLE, "Answer", "Subtitle", "Because I misunderstood.");

	    answer.addReference(new ParagraphReference(answeredQuestion.getId()));
		
		ConversationGraph graph = new ConversationGraph();
		
		  graph.addParagraph(answeredQuestion);
		  graph.addParagraph(answer);
		  graph.addParagraph(unansweredQuestion);

		    QuestionTracker tracker = new QuestionTracker(graph);

		    assertTrue(tracker.isAnswered(answeredQuestion));
		
	}
}
