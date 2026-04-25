package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.agreement.Agreement;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class conversationTest
{
	@Test
	void shouldStartInPendeingAgreementStatus()
	{
		Couple couple = new Couple(
																1L,
																new UserId(10L),
																new UserId(20L),
																new UserId(30L)
																);
		Agreement agreement = new Agreement(1L, "Initial Agrement");
		
		Conversation conversation = new Conversation(1L, couple.getId(), agreement);
		
		assertFalse(conversation.canSendMessage(new UserId(10L), couple));
		
	}
}
