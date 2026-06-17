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
		assertThrows(DomainException.class, () -> new SimpleParagraph(null, ParagraphType.SIMPLE, "Title", "SubTitle", "Body")); 
	}
	
	@Test
	void shouldThrowExceptionWhenAddNullParagraph()
	{
		SimpleParagraph paragraph = 
				new SimpleParagraph(new ParagraphId(20L), ParagraphType.SIMPLE, "TItle", "SubTitle", "Body");
		
		
		assertThrows(DomainException.class, () -> paragraph.addReference(null));
	}
	
	@Test
	void shouldCorrectlyAddReference()
	{
		SimpleParagraph paragraph = 
				new SimpleParagraph(new ParagraphId(20L), ParagraphType.SIMPLE, "TItle", "SubTitle", "Body");
		
		
		SimpleParagraph referencingParagraph = new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "text", "text", "text");
		
		paragraph.addReference(referencingParagraph);		
		assertEquals(1, paragraph.getReferences().size()); 
	}
	 @Test
	 void shouldThrowExceptionWhenSelfRefenced()
	 {
			var paragraph = new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "TItle", "SubTitle", "Body");
			
			SimpleParagraph referencingParagraph = new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "text", "text", "text");
			
			assertThrows(DomainException.class, () -> paragraph.addReference(referencingParagraph));
	 }
	 
	 @Test
	 void shouldThrowExceptionWhenRefenceAlredyExist()
	 {
			var paragraph = new SimpleParagraph(new ParagraphId(10L), ParagraphType.SIMPLE, "TItle", "SubTitle", "Body");
			
			SimpleParagraph firstReferencingParagraph = new SimpleParagraph(new ParagraphId(20L), ParagraphType.SIMPLE, "text", "text", "text");
			
			SimpleParagraph secondReferencingParagraph = new SimpleParagraph(new ParagraphId(20L), ParagraphType.SIMPLE, "text", "text", "text");
			
			paragraph.addReference(firstReferencingParagraph);
			
			assertThrows(DomainException.class, () -> paragraph.addReference(secondReferencingParagraph));
	 }
	 
}
