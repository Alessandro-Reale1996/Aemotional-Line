package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.agreement.Agreement;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.ConstraintSet;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.SimpleParagraph;
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
	void shouldCreateConversationWithEmptyConstraintSet()
	{
	    UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    Agreement agreement = new Agreement(1L, "Initial agreement");

	    Conversation conversation = new Conversation(1L, couple.getId(), agreement);

	    assertNotNull(conversation.getConstraintSet());
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
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		
		assertThrows(DomainException.class, () -> conversation.applyConstraints(constraintChangeRequest, couple));
	}
	
	@Test
	void shouldBlockMessageWhenConstraintFails()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId(30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		Agreement agreement = new Agreement(1L, "Initial agreement");
		Conversation conversation = new Conversation(1L, couple.getId(), agreement);
		
		conversation.acceptAgrement(partnerOne, couple);
		conversation.acceptAgrement(partnerTwo, couple);
		
		TimeConstraint failConstraint = new TimeConstraint(LocalTime.of(23, 0));

		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, failConstraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		constraintChangeRequest.approve(partnerTwo, couple);
		
		conversation.applyConstraints(constraintChangeRequest, couple);
		
		Message message = new Message(partnerOne);
		
		  assertThrows( DomainException.class, () -> conversation.sendMessage(message, couple, new ConstraintContext(LocalTime.of(22, 0))));
	}
	
	@Test
	void shouldAddMessageToConversation()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId(30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		Agreement agreement = new Agreement(1L, "Initial agreement");
		Conversation conversation = new Conversation(1L, couple.getId(), agreement);
		
		conversation.acceptAgrement(partnerOne, couple);
		conversation.acceptAgrement(partnerTwo, couple);
		
		TimeConstraint Constraint = new TimeConstraint(LocalTime.of(22, 0));
				
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, Constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		constraintChangeRequest.approve(partnerTwo, couple);
		
		conversation.applyConstraints(constraintChangeRequest, couple);
		
		Message message = new Message(partnerOne);
		
		Paragraph paragraph = new SimpleParagraph(ParagraphType.SIMPLE, "title", "subtitle", "body");
		
		message.addParagraph(paragraph);
		
		conversation.sendMessage(message, couple, new ConstraintContext(LocalTime.of(23, 0)));
		
	    assertEquals(1, conversation.getMessages().size());
	}
	
	@Test
	void shuldNotSendEmptyMessage()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId(30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		Agreement agreement = new Agreement(1L, "Initial agreement");
		Conversation conversation = new Conversation(1L, couple.getId(), agreement);
		
		conversation.acceptAgrement(partnerOne, couple);
		conversation.acceptAgrement(partnerTwo, couple);
		
		TimeConstraint Constraint = new TimeConstraint(LocalTime.of(22, 0));
				
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, Constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		constraintChangeRequest.approve(partnerTwo, couple);
		
		conversation.applyConstraints(constraintChangeRequest, couple);
		
		Message message = new Message(partnerOne);
		
		  assertThrows(DomainException.class,
				  () -> conversation.sendMessage(
		                    message,
		                    couple,
		                    new ConstraintContext(LocalTime.of(23, 0))
		            )
		    );
	}
	
}
