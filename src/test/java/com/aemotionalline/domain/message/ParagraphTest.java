package com.aemotionalline.domain.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

public class ParagraphTest
{
	@Test
	void sholdThrowExceptionWhenParagraphIdIsNull()
	{		
		assertThrows(DomainException.class, () -> new SimpleParagraph(null, "Title", "SubTitle", "Body")); 
	}
	
	@Test
	void shouldThrowExceptionWhenAddNullParagraph()
	{
		SimpleParagraph paragraph = 
				new SimpleParagraph(new ParagraphId(20L), "TItle", "SubTitle", "Body");
		
		
		assertThrows(DomainException.class, () -> paragraph.addReference(null));
	}
	
	@Test
	void shouldCorrectlyAddReference()
	{
		SimpleParagraph paragraph = 
				new SimpleParagraph(new ParagraphId(20L), "TItle", "SubTitle", "Body");
		
		
		SimpleParagraph referencingParagraph = new SimpleParagraph(new ParagraphId(10L), "text", "text", "text");
		
		paragraph.addReference(referencingParagraph);		
		assertEquals(1, paragraph.getReferences().size()); 
	}
	 @Test
	 void shouldThrowExceptionWhenSelfRefenced()
	 {
			var paragraph = new SimpleParagraph(new ParagraphId(10L), "TItle", "SubTitle", "Body");
			
			SimpleParagraph referencingParagraph = new SimpleParagraph(new ParagraphId(10L), "text", "text", "text");
			
			assertThrows(DomainException.class, () -> paragraph.addReference(referencingParagraph));
	 }
	 
	 @Test
	 void shouldThrowExceptionWhenRefenceAlredyExist()
	 {
			var paragraph = new SimpleParagraph(new ParagraphId(10L), "TItle", "SubTitle", "Body");
			
			SimpleParagraph firstReferencingParagraph = new SimpleParagraph(new ParagraphId(20L), "text", "text", "text");
			
			SimpleParagraph secondReferencingParagraph = new SimpleParagraph(new ParagraphId(20L), "text", "text", "text");
			
			paragraph.addReference(firstReferencingParagraph);
			
			assertThrows(DomainException.class, () -> paragraph.addReference(secondReferencingParagraph));
	 }

	 @Test
	 void shouldRejectQuestionTitleLongerThanTitleLimit()
	 {
			// 251 characters: above the 250 title limit but below the 350 body limit, which the title check once used by mistake.
			String longTitle = "t".repeat(251);

			assertThrows(DomainException.class, () -> new QuestionParagraph(new ParagraphId(1L), longTitle, "Body"));
	 }

	 @Test
	 void shouldAcceptQuestionTitleAtTitleLimit()
	 {
			String maxTitle = "t".repeat(250);

			new QuestionParagraph(new ParagraphId(1L), maxTitle, "Body");
	 }

	 @Test
	 void shouldReportTypeMatchingItsSubclass()
	 {
			assertEquals(ParagraphType.SIMPLE, new SimpleParagraph(new ParagraphId(1L), "Title", "SubTitle", "Body").getType());
			assertEquals(ParagraphType.QUESTION, new QuestionParagraph(new ParagraphId(2L), "Title", "Body").getType());
			assertEquals(ParagraphType.POINTED, new PointedParagraph(new ParagraphId(3L), "Title", "Body").getType());
	 }

	 @Test
	 void shouldAllowAPointedParagraphToAnswerOnlyOneQuestion()
	 {
			PointedParagraph answer = new PointedParagraph(new ParagraphId(1L), "In risposta a...", "Body");
			QuestionParagraph firstQuestion = new QuestionParagraph(new ParagraphId(2L), "Why?", "Body");
			QuestionParagraph secondQuestion = new QuestionParagraph(new ParagraphId(3L), "When?", "Body");
			
			answer.addReference(firstQuestion);
			
			assertThrows(DomainException.class, () -> answer.addReference(secondQuestion));
			assertEquals(1, answer.getReferences().size());
	 }

	 @Test
	 void shouldNotChangeTheReferencesOfASealedParagraph()
	 {
			SimpleParagraph earlier = new SimpleParagraph(new ParagraphId(1L), "Title", "SubTitle", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(new ParagraphId(2L), "Title", "SubTitle", "Body");
			PointedParagraph answer = new PointedParagraph(new ParagraphId(3L), "In risposta a...", "Body");
			QuestionParagraph question = new QuestionParagraph(new ParagraphId(4L), "Why?", "Body");
			
			Message message = new Message(new MessageId(1L), new UserId(10L));
			message.addParagraph(paragraph);
			message.addParagraph(answer);
			message.seal();
			
			assertTrue(paragraph.isSealed());
			assertThrows(DomainException.class, () -> paragraph.addReference(earlier));
			// Subclasses check the seal before their own rules.
			assertThrows(DomainException.class, () -> answer.addReference(question));
			assertTrue(paragraph.getReferences().isEmpty());
	 }

	 // CITATIONS (5g): a separate kind of link from references.
	 
	 @Test
	 void shouldKeepCitationsSeparateFromReferences()
	 {
			SimpleParagraph cited = new SimpleParagraph(new ParagraphId(1L), "Title", "SubTitle", "Body");
			SimpleParagraph referenced = new SimpleParagraph(new ParagraphId(2L), "Title", "SubTitle", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(new ParagraphId(3L), "Title", "SubTitle", "Body");
			
			paragraph.addCitation(cited);
			paragraph.addReference(referenced);
			
			assertEquals(List.of(cited), paragraph.getCitations());
			assertEquals(List.of(referenced), paragraph.getReferences());
	 }
	 
	 @Test
	 void shouldAllowAnyParagraphToCiteAQuestion()
	 {
			QuestionParagraph question = new QuestionParagraph(new ParagraphId(1L), "Why?", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(new ParagraphId(2L), "Title", "SubTitle", "Body");
			
			// Only a pointed paragraph may reference a question, but a citation answers nothing.
			paragraph.addCitation(question);
			
			assertEquals(1, paragraph.getCitations().size());
	 }
	 
	 @Test
	 void shouldRejectInvalidCitations()
	 {
			SimpleParagraph cited = new SimpleParagraph(new ParagraphId(1L), "Title", "SubTitle", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(new ParagraphId(2L), "Title", "SubTitle", "Body");
			
			paragraph.addCitation(cited);
			
			assertThrows(DomainException.class, () -> paragraph.addCitation(null));
			assertThrows(DomainException.class, () -> paragraph.addCitation(paragraph));
			assertThrows(DomainException.class, () -> paragraph.addCitation(cited));
	 }
	 
	 @Test
	 void shouldNotAddCitationsToASealedParagraph()
	 {
			SimpleParagraph earlier = new SimpleParagraph(new ParagraphId(1L), "Title", "SubTitle", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(new ParagraphId(2L), "Title", "SubTitle", "Body");
			
			Message message = new Message(new MessageId(1L), new UserId(10L));
			message.addParagraph(paragraph);
			message.seal();
			
			assertThrows(DomainException.class, () -> paragraph.addCitation(earlier));
	 }

}
