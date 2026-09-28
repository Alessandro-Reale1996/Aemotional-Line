package com.aemotionalline.domain.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;

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

}
