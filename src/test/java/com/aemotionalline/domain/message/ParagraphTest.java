package com.aemotionalline.domain.message;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.Ids;

public class ParagraphTest
{
	@Test
	void shouldThrowExceptionWhenParagraphIdIsNull()
	{		
		assertThrows(DomainException.class, () -> new SimpleParagraph(null, "Title", "SubTitle", "Body")); 
	}
	
	@Test
	void shouldThrowExceptionWhenAddNullParagraph()
	{
		SimpleParagraph paragraph = 
				new SimpleParagraph(Ids.paragraph(20L), "TItle", "SubTitle", "Body");
		
		
		assertThrows(DomainException.class, () -> paragraph.addReference(null));
	}
	
	@Test
	void shouldCorrectlyAddReference()
	{
		SimpleParagraph paragraph = 
				new SimpleParagraph(Ids.paragraph(20L), "TItle", "SubTitle", "Body");
		
		
		SimpleParagraph referencingParagraph = new SimpleParagraph(Ids.paragraph(10L), "text", "text", "text");
		
		paragraph.addReference(referencingParagraph);		
		assertEquals(1, paragraph.getReferences().size()); 
	}
	 @Test
	 void shouldThrowExceptionWhenSelfReferenced()
	 {
			var paragraph = new SimpleParagraph(Ids.paragraph(10L), "TItle", "SubTitle", "Body");
			
			SimpleParagraph referencingParagraph = new SimpleParagraph(Ids.paragraph(10L), "text", "text", "text");
			
			assertThrows(DomainException.class, () -> paragraph.addReference(referencingParagraph));
	 }
	 
	 @Test
	 void shouldThrowExceptionWhenReferenceAlreadyExists()
	 {
			var paragraph = new SimpleParagraph(Ids.paragraph(10L), "TItle", "SubTitle", "Body");
			
			SimpleParagraph firstReferencingParagraph = new SimpleParagraph(Ids.paragraph(20L), "text", "text", "text");
			
			SimpleParagraph secondReferencingParagraph = new SimpleParagraph(Ids.paragraph(20L), "text", "text", "text");
			
			paragraph.addReference(firstReferencingParagraph);
			
			assertThrows(DomainException.class, () -> paragraph.addReference(secondReferencingParagraph));
	 }

	 @Test
	 void shouldRejectQuestionTitleLongerThanTitleLimit()
	 {
			// 251 characters: above the 250 title limit but below the 350 body limit, which the title check once used by mistake.
			String longTitle = "t".repeat(251);

			assertThrows(DomainException.class, () -> new QuestionParagraph(Ids.paragraph(1L), longTitle, "Body"));
	 }

	 @Test
	 void shouldAcceptQuestionTitleAtTitleLimit()
	 {
			String maxTitle = "t".repeat(250);

			assertDoesNotThrow(() -> new QuestionParagraph(Ids.paragraph(1L), maxTitle, "Body"));
	 }

	 @Test
	 void shouldReportTypeMatchingItsSubclass()
	 {
			assertEquals(ParagraphType.SIMPLE, new SimpleParagraph(Ids.paragraph(1L), "Title", "SubTitle", "Body").getType());
			assertEquals(ParagraphType.QUESTION, new QuestionParagraph(Ids.paragraph(2L), "Title", "Body").getType());
			assertEquals(ParagraphType.POINTED, new PointedParagraph(Ids.paragraph(3L), new QuestionParagraph(Ids.paragraph(4L), "Title", "Body"), "Body").getType());
	 }

	 @Test
	 void shouldAllowAPointedParagraphToAnswerOnlyOneQuestion()
	 {
			QuestionParagraph firstQuestion = new QuestionParagraph(Ids.paragraph(2L), "Why?", "Body");
			QuestionParagraph secondQuestion = new QuestionParagraph(Ids.paragraph(3L), "When?", "Body");
			PointedParagraph answer = new PointedParagraph(Ids.paragraph(1L), firstQuestion, "Body");
			
			assertThrows(DomainException.class, () -> answer.addReference(secondQuestion));
			assertEquals(1, answer.getReferences().size());
	 }

	 @Test
	 void shouldNotChangeTheReferencesOfASealedParagraph()
	 {
			SimpleParagraph earlier = new SimpleParagraph(Ids.paragraph(1L), "Title", "SubTitle", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(Ids.paragraph(2L), "Title", "SubTitle", "Body");
			QuestionParagraph question = new QuestionParagraph(Ids.paragraph(4L), "Why?", "Body");
			PointedParagraph answer = new PointedParagraph(Ids.paragraph(3L), new QuestionParagraph(Ids.paragraph(5L), "When?", "Body"), "Body");
			
			Message message = new Message(Ids.message(1L), Ids.user(10L));
			message.addParagraph(paragraph);
			message.addParagraph(answer);
			message.seal();
			
			assertTrue(paragraph.isSealed());
			assertThrows(DomainException.class, () -> paragraph.addReference(earlier));
			// Subclasses check the seal before their own rules.
			assertThrows(DomainException.class, () -> answer.addReference(question));
			assertTrue(paragraph.getReferences().isEmpty());
	 }

	 // SELF-CITATIONS (5g): a separate kind of link from references.
	 
	 @Test
	 void shouldKeepSelfCitationsSeparateFromReferences()
	 {
			SimpleParagraph cited = new SimpleParagraph(Ids.paragraph(1L), "Title", "SubTitle", "Body");
			SimpleParagraph referenced = new SimpleParagraph(Ids.paragraph(2L), "Title", "SubTitle", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(Ids.paragraph(3L), "Title", "SubTitle", "Body");
			
			paragraph.addSelfCitation(cited);
			paragraph.addReference(referenced);
			
			assertEquals(List.of(cited), paragraph.getSelfCitations());
			assertEquals(List.of(referenced), paragraph.getReferences());
	 }
	 
	 @Test
	 void shouldAllowAnyParagraphToCiteAQuestion()
	 {
			QuestionParagraph question = new QuestionParagraph(Ids.paragraph(1L), "Why?", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(Ids.paragraph(2L), "Title", "SubTitle", "Body");
			
			// Only a pointed paragraph may reference a question, but a self-citation answers nothing.
			paragraph.addSelfCitation(question);
			
			assertEquals(1, paragraph.getSelfCitations().size());
	 }
	 
	 @Test
	 void shouldRejectInvalidSelfCitations()
	 {
			SimpleParagraph cited = new SimpleParagraph(Ids.paragraph(1L), "Title", "SubTitle", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(Ids.paragraph(2L), "Title", "SubTitle", "Body");
			
			paragraph.addSelfCitation(cited);
			
			assertThrows(DomainException.class, () -> paragraph.addSelfCitation(null));
			assertThrows(DomainException.class, () -> paragraph.addSelfCitation(paragraph));
			assertThrows(DomainException.class, () -> paragraph.addSelfCitation(cited));
	 }
	 
	 @Test
	 void shouldNotAddSelfCitationsToASealedParagraph()
	 {
			SimpleParagraph earlier = new SimpleParagraph(Ids.paragraph(1L), "Title", "SubTitle", "Body");
			SimpleParagraph paragraph = new SimpleParagraph(Ids.paragraph(2L), "Title", "SubTitle", "Body");
			
			Message message = new Message(Ids.message(1L), Ids.user(10L));
			message.addParagraph(paragraph);
			message.seal();
			
			assertThrows(DomainException.class, () -> paragraph.addSelfCitation(earlier));
	 }

	 // POINTED PARAGRAPHS (section C, point 2): the question is given at creation and sets the standard title.
	 
	 @Test
	 void shouldBuildTheStandardTitleFromTheQuestion()
	 {
			QuestionParagraph question = new QuestionParagraph(Ids.paragraph(1L), "Perch\u00e9 sei arrabbiato?", "Body");
			
			PointedParagraph answer = new PointedParagraph(Ids.paragraph(2L), question, "Body");
			
			assertEquals("In risposta a \u00abPerch\u00e9 sei arrabbiato?\u00bb", answer.getTitle());
			assertNull(answer.getSubtitle());
	 }
	 
	 @Test
	 void shouldReferenceTheQuestionGivenAtCreation()
	 {
			QuestionParagraph question = new QuestionParagraph(Ids.paragraph(1L), "Why?", "Body");
			
			PointedParagraph answer = new PointedParagraph(Ids.paragraph(2L), question, "Body");
			
			assertEquals(question, answer.getQuestion());
			assertEquals(List.of(question), answer.getReferences());
	 }
	 
	 @Test
	 void shouldNotBuildAPointedParagraphWithoutAQuestion()
	 {
			assertThrows(DomainException.class, () -> new PointedParagraph(Ids.paragraph(1L), null, "Body"));
	 }

}
