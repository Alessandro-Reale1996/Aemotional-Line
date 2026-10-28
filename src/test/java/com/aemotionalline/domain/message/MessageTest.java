package com.aemotionalline.domain.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.Ids;

public class MessageTest
{
	private static final Instant SENT_AT = Instant.parse("2026-10-28T10:00:00Z");

	@Test
	void shouldThrowExceptionWhenTryToModifyParagraphs()
	{
		UserId user = Ids.user(1L);
		
		Message message = new Message(Ids.message(100L), user);
		
		assertThrows(UnsupportedOperationException.class, () -> message.getParagraphs().clear());
	}
	
	@Test
	void shouldThrowExceptionWhenParagraphsIsEmpty()
	{
		UserId user = Ids.user(1L);
		
		Message message = new Message(Ids.message(100L), user);
		
		assertThrows(DomainException.class, () -> message.ensureReadyToSend());
	}
	
	@Test
	void shouldBeReadyToSendWithAtLeastOneParagraph()
	{
		UserId user = Ids.user(1L);
		
		Message message = new Message(Ids.message(100L), user);
		
		Paragraph paragraph = new SimpleParagraph(Ids.paragraph(1L), "title", "subtitle", "body");
		
		message.addParagraph(paragraph);
		
		assertTrue(message.ensureReadyToSend());
	}
	
	@Test
	void shouldThrowExceptionWhenParagraphIsNull()
	{
		UserId user = Ids.user(1L);
		
		Message message = new Message(Ids.message(100L), user);
		
		Paragraph paragraph = null;
		
		assertThrows(DomainException.class, () -> message.addParagraph(paragraph));
	}
	
	@Test
	void shouldAddParagraphToMessage()
	{
		UserId user = Ids.user(1L);
		
		Message message = new Message(Ids.message(100L), user);
		
		Paragraph paragraph = new SimpleParagraph(Ids.paragraph(1L), "title", "subtitle", "body");
		
		message.addParagraph(paragraph);
		
		assertEquals(1, message.getParagraphs().size());
	
	}

	@Test
	void shouldNotAddParagraphsToASealedMessage()
	{
		Message message = new Message(Ids.message(1L), Ids.user(10L));
		message.addParagraph(new SimpleParagraph(Ids.paragraph(1L), "Title", "SubTitle", "Body"));
		
		message.seal(List.of(), SENT_AT);
		
		assertTrue(message.isSealed());
		assertThrows(DomainException.class, () -> message.addParagraph(new SimpleParagraph(Ids.paragraph(2L), "Title", "SubTitle", "Body")));
		assertEquals(1, message.getParagraphs().size());
	}

	@Test
	void shouldSealOnlyOnceSoTheRecordOfSkippedQuestionsCantChange()
	{
		Message message = new Message(Ids.message(1L), Ids.user(10L));
		message.addParagraph(new SimpleParagraph(Ids.paragraph(1L), "Title", "SubTitle", "Body"));
		
		message.seal(List.of(Ids.paragraph(9L)), SENT_AT);
		
		assertEquals(List.of(Ids.paragraph(9L)), message.getQuestionsLeftUnanswered());
		assertThrows(DomainException.class, () -> message.seal(List.of(), SENT_AT.plusSeconds(60)));
		assertEquals(List.of(Ids.paragraph(9L)), message.getQuestionsLeftUnanswered());
		assertEquals(SENT_AT, message.getSentAt());
	}
	
	@Test
	void shouldRejectANullRecordOfSkippedQuestions()
	{
		Message message = new Message(Ids.message(1L), Ids.user(10L));
		
		assertThrows(DomainException.class, () -> message.seal(null, SENT_AT));
		assertThrows(DomainException.class, () -> message.seal(Arrays.asList((ParagraphId) null), SENT_AT));
		assertTrue(!message.isSealed());
	}
	
	@Test
	void shouldRecordWhenTheMessageWasSentAndRequireIt()
	{
		Message message = new Message(Ids.message(1L), Ids.user(10L));
		message.addParagraph(new SimpleParagraph(Ids.paragraph(1L), "Title", "SubTitle", "Body"));
		
		assertEquals(null, message.getSentAt());
		assertThrows(DomainException.class, () -> message.seal(List.of(), null));
		assertTrue(!message.isSealed());
		
		message.seal(List.of(), SENT_AT);
		
		assertEquals(SENT_AT, message.getSentAt());
	}

}
