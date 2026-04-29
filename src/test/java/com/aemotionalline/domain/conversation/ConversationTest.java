package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.agreement.Agreement;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.Constraint;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintContext;
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
	void shouldNotAllowTherapistToSendMessageAsPartner()
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
	
	@Test
	void shouldThrowExceptionWhenConstrictionsAreNotFullApproved()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId(30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		Agreement agreement = new Agreement(1L, "Initial agreement");
		Conversation conversation = new Conversation(1L, couple.getId(), agreement);
		
		Constraint constraint = new Constraint("Test String");
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		
		assertThrows(DomainException.class, () -> conversation.applyConstraints(constraintChangeRequest, couple));
	}
	
	@Test
	void houldBlockMessageWhenConstraintFails()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId(30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		Agreement agreement = new Agreement(1L, "Initial agreement");
		Conversation conversation = new Conversation(1L, couple.getId(), agreement);
		
		Constraint failConstraint = 
				new Constraint("Test String")
				{
					@Override
					public boolean isSatisfied(ConstraintContext context) 
					{
						return false;
					}
				};
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, failConstraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		constraintChangeRequest.approve(partnerTwo, couple);
		
		conversation.applyConstraints(constraintChangeRequest, couple);
		
		  assertThrows( DomainException.class, () -> conversation.sendMessage(partnerOne, couple, new ConstraintContext(10)));
	}
	
}
