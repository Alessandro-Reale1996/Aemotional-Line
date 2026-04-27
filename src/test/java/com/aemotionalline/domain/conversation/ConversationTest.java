package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.agreement.Agreement;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class ConversationTest
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
	
	@Test
	void shouldActivateConversationWhenBothPartnersAcceptAgreement()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId (30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		Agreement agreement = new Agreement(1L, "Initial agreement");
		Conversation conversation = new Conversation(1L, couple.getId(), agreement);
		
		conversation.acceptAgrement(partnerOne, couple);
		assertEquals(ConversationStatus.PENDING_AGREEMENT, conversation.getStatus());
		 
		conversation.acceptAgrement(partnerTwo, couple);
		assertEquals(ConversationStatus.ACTIVE, conversation.getStatus());
	}
	
	@Test
	void shouldNotAlloowTherapistToSendMessageAsPartner()
	{
		
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId (30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		Agreement agreement = new Agreement(1L, "Initial agreement");
		Conversation conversation = new Conversation(1L, couple.getId(), agreement);
		
		conversation.acceptAgrement(partnerOne, couple);
		conversation.acceptAgrement(partnerTwo, couple);
		
		assertFalse(conversation.canSendMessage(therapist, couple));
			
	}
	
	@Test
	void therapistShouldNotAcceptAgreement()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId (30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		Agreement agreement = new Agreement(1L, "Initial agreement");
		
		assertThrows(DomainException.class, () -> agreement.accept(therapist, couple));
	}
	
	@Test
	void shouldThrowExceptionWhenSendingIsNotAllowed()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId (30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		Agreement agreement = new Agreement(1L, "Initial agreement");
		Conversation conversation = new Conversation(1L, couple.getId(), agreement);
		
		assertThrows(DomainException.class, () -> conversation.ensureCanSendMessage(partnerOne, couple));
	}
}
