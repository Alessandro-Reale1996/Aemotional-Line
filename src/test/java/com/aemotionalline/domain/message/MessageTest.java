package com.aemotionalline.domain.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

public class MessageTest
{
	@Test
	void shouldThrowExceptionWhenTryToModifyParagraphs()
	{
		UserId user = new UserId(1L);
		
		Message message = new Message(user);
		
		assertThrows(UnsupportedOperationException.class, () -> message.getParagraphs().clear());
	}
	
	@Test
	void shouldThrowExceptionWhenParagraphsIsEmpty()
	{
		UserId user = new UserId(1L);
		
		Message message = new Message(user);
		
		assertThrows(DomainException.class, () -> message.ensureReadyToSend());
	}
	
	@Test
	void shouldBeReadyToSendWithAtLeastOneParagraph()
	{
		UserId user = new UserId(1L);
		
		Message message = new Message(user);
		
		Paragraph paragraph = new SimpleParagraph(new ParagraphId(1L), ParagraphType.SIMPLE, "title", "subtitle", "body");
		
		message.addParagraph(paragraph);
		
		assertTrue(message.ensureReadyToSend());
	}
	
	@Test
	void shouldThrowExceptionWhenParagraphIsNull()
	{
		UserId user = new UserId(1L);
		
		Message message = new Message(user);
		
		Paragraph paragraph = null;
		
		assertThrows(DomainException.class, () -> message.addParagraph(paragraph));
	}
	
	@Test
	void shouldAddParagraphToMessage()
	{
		UserId user = new UserId(1L);
		
		Message message = new Message(user);
		
		Paragraph paragraph = new SimpleParagraph(new ParagraphId(1L), ParagraphType.SIMPLE, "title", "subtitle", "body");
		
		message.addParagraph(paragraph);
		
		assertEquals(1, message.getParagraphs().size());
	
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
