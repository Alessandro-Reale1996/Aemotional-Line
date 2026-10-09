package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.Ids;

public class ConversationTest
{
	
	
	@Test
	void shouldCreateConversationWithEmptyConstraintSet()
	{
	    UserId partnerOne = Ids.user(10L);
	    UserId partnerTwo = Ids.user(20L);
	    UserId therapist = Ids.user(30L);

	    Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = new AgreementProposal(Ids.proposal(123L), couple.getPartnerOneId(), "TEXT");
	    
	    Negotiation negotiation = Negotiation.start(Ids.negotiation(60L), couple,initialProposal, Clock.systemUTC());
	    
	    negotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    negotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(Ids.conversation(321L), couple, negotiation);

	    assertNotNull(conversation.getConstraintSet());
	}
	

	
	@Test
	void shouldNotAllowTherapistToSendMessageAsPartner()
	{
		
		UserId partnerOne = Ids.user(10L);
	    UserId partnerTwo = Ids.user(20L);
	    UserId therapist = Ids.user(30L);

	    Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = new AgreementProposal(Ids.proposal(123L), couple.getPartnerOneId(), "TEXT");
	    
	    Negotiation negotiation = Negotiation.start(Ids.negotiation(60L), couple,initialProposal, Clock.systemUTC());
	    
	    negotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    negotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(Ids.conversation(321L), couple, negotiation);
		
		assertFalse(conversation.canSendMessage(therapist));
			
	}
	
	
	
	@Test
	void shouldAddDiscussionToConversation()
	{
		UserId partnerOne = Ids.user(10L);
	    UserId partnerTwo = Ids.user(20L);
	    UserId therapist = Ids.user(30L);

	    Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = new AgreementProposal(Ids.proposal(123L), couple.getPartnerOneId(), "TEXT");
	    
	    Negotiation conversationNegotiation = Negotiation.start(Ids.negotiation(60L), couple,initialProposal, Clock.systemUTC());
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(Ids.conversation(321L), couple, conversationNegotiation);
	 
	    Proposal discussionProposal = new DiscussionProposal(Ids.proposal(124L), couple.getPartnerOneId(), "TITLE", "SUBTITLE");

	    Negotiation discussionNegotiation = Negotiation.start(Ids.negotiation(70L), couple, discussionProposal, Clock.systemUTC());
	    
	    discussionNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    discussionNegotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    discussionNegotiation.acceptProposal(couple.getPartnerTwoId());
		
		conversation.openDiscussion(discussionNegotiation);
		
		assertEquals(2, conversation.getDiscussions().size());
	}
	

	
	
	// A NEGOTIATION CAN ONLY BE USED BY THE CONVERSATION OF ITS OWN COUPLE:
	
	private static Negotiation acceptedNegotiation(long negotiationId, long proposalId, Couple couple)
	{
		Proposal proposal = new AgreementProposal(Ids.proposal(proposalId), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(Ids.negotiation(negotiationId), couple, proposal, Clock.systemUTC());
		
		negotiation.acceptNegotiation(couple.getPartnerTwoId());
		
		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
		
		negotiation.acceptProposal(couple.getPartnerTwoId());
		
		return negotiation;
	}
	
	@Test
	void shouldThrowExceptionWhenStartingWithAnotherCouplesNegotiation()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		Couple otherCouple = new Couple(Ids.couple(2L), Ids.user(40L), Ids.user(50L), Ids.user(30L));
		
		Negotiation otherNegotiation = acceptedNegotiation(60L, 123L, otherCouple);
		
		assertThrows(DomainException.class, () -> Conversation.start(Ids.conversation(321L), couple, otherNegotiation));
	}
	
	@Test
	void shouldThrowExceptionWhenOpeningDiscussionWithAnotherCouplesNegotiation()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		Couple otherCouple = new Couple(Ids.couple(2L), Ids.user(40L), Ids.user(50L), Ids.user(30L));
		
		Conversation conversation = Conversation.start(Ids.conversation(321L), couple, acceptedNegotiation(60L, 123L, couple));
		
		Negotiation otherNegotiation = acceptedNegotiation(70L, 124L, otherCouple);
		
		assertThrows(DomainException.class, () -> conversation.openDiscussion(otherNegotiation));
		
		assertEquals(1, conversation.getDiscussions().size());
	}
	
	@Test
	void shouldThrowExceptionWhenModifyingAgreementWithAnotherCouplesNegotiation()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		Couple otherCouple = new Couple(Ids.couple(2L), Ids.user(40L), Ids.user(50L), Ids.user(30L));
		
		Negotiation negotiation = acceptedNegotiation(60L, 123L, couple);
		
		Conversation conversation = Conversation.start(Ids.conversation(321L), couple, negotiation);
		
		Negotiation otherNegotiation = acceptedNegotiation(70L, 124L, otherCouple);
		
		assertThrows(DomainException.class, () -> conversation.modifyAgreement(otherNegotiation));
		
		assertEquals(negotiation.getLastProposal(), conversation.getAgreement());
	}
	
	@Test
	void shouldAcceptNegotiationOfTheSameCoupleLoadedAsAnotherObject()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		Couple sameCoupleLoadedAgain = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		
		Conversation conversation = Conversation.start(Ids.conversation(321L), couple, acceptedNegotiation(60L, 123L, sameCoupleLoadedAgain));
		
		assertEquals(1, conversation.getDiscussions().size());
	}
	
	@Test
	void shouldThrowExceptionWhenSendingIsNotAllowed()
	{
		UserId partnerOne = Ids.user(10L);
	    UserId partnerTwo = Ids.user(20L);
	    UserId therapist = Ids.user(30L);

	    Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = new AgreementProposal(Ids.proposal(123L), couple.getPartnerOneId(), "TEXT");
	    
	    Negotiation conversationNegotiation = Negotiation.start(Ids.negotiation(60L), couple,initialProposal, Clock.systemUTC());
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(Ids.conversation(321L), couple, conversationNegotiation);
		
		assertThrows(DomainException.class, () -> conversation.ensureCanSendMessage(therapist));
	}
	
	@Test
	void shouldThrowExceptionWhenConstraintsAreNotFullyApproved()
	{
		UserId partnerOne = Ids.user(10L);
	    UserId partnerTwo = Ids.user(20L);
	    UserId therapist = Ids.user(30L);

	    Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = new AgreementProposal(Ids.proposal(123L), couple.getPartnerOneId(), "TEXT");
	    
	    Negotiation conversationNegotiation = Negotiation.start(Ids.negotiation(60L), couple,initialProposal, Clock.systemUTC());
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(Ids.conversation(321L), couple, conversationNegotiation);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne);
		
		assertThrows(DomainException.class, () -> conversation.applyConstraints(constraintChangeRequest));
	}
	
	@Test
	void shouldBlockMessageWhenConstraintFails()
	{
		UserId partnerOne = Ids.user(10L);
	    UserId partnerTwo = Ids.user(20L);
	    UserId therapist = Ids.user(30L);

	    Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = new AgreementProposal(Ids.proposal(123L), couple.getPartnerOneId(), "TEXT");
	    
	    Negotiation conversationNegotiation = Negotiation.start(Ids.negotiation(60L), couple,initialProposal, Clock.systemUTC());
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(Ids.conversation(321L), couple, conversationNegotiation);
		
		TimeConstraint failConstraint = new TimeConstraint(LocalTime.of(23, 0));

		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, failConstraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne);
		constraintChangeRequest.approve(partnerTwo);
		
		conversation.applyConstraints(constraintChangeRequest);
		
		Message message = new Message(Ids.message(110L),partnerOne);
		
		DiscussionId discussionId = conversation.getDiscussions().getFirst().getId();
		
		message.addParagraph(new SimpleParagraph(Ids.paragraph(1L), "title", "subtitle", "body"));

		// The message is valid, so the only reason left to reject it is the time constraint.
		DomainException exception = assertThrows(DomainException.class, () -> conversation.sendMessage(message, discussionId, new ConstraintContext(LocalTime.of(22, 0))));

		assertEquals("Constraint violated", exception.getMessage());
	}
	
	@Test
	void shouldAddMessageToConversation()
	{
		UserId partnerOne = Ids.user(10L);
	    UserId partnerTwo = Ids.user(20L);
	    UserId therapist = Ids.user(30L);

	    Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = new AgreementProposal(Ids.proposal(123L), couple.getPartnerOneId(), "TEXT");
	    
	    Negotiation conversationNegotiation = Negotiation.start(Ids.negotiation(60L), couple,initialProposal, Clock.systemUTC());
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(Ids.conversation(321L), couple, conversationNegotiation);
		
		TimeConstraint Constraint = new TimeConstraint(LocalTime.of(22, 0));
				
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, Constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne);
		constraintChangeRequest.approve(partnerTwo);
		
		conversation.applyConstraints(constraintChangeRequest);
		
		Message message = new Message(Ids.message(110L),partnerOne);
		
		
		Paragraph paragraph = new SimpleParagraph(Ids.paragraph(1L), "title", "subtitle", "body");
		
		message.addParagraph(paragraph);
		
		Discussion discussion = conversation.getDiscussions().getFirst();
		
		conversation.sendMessage(message, discussion.getId(), new ConstraintContext(LocalTime.of(23, 0)));
		
	    assertEquals(message, conversation.findDiscussion(discussion.getId()).getMessages().getFirst());
	}
	
	@Test
	void shouldThrowExceptionWhenSenderIsTheSameAsLastSender()
	{
		UserId partnerOne = Ids.user(10L);
	    UserId partnerTwo = Ids.user(20L);
	    UserId therapist = Ids.user(30L);

	    Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = new AgreementProposal(Ids.proposal(123L), couple.getPartnerOneId(), "TEXT");
	    
	    Negotiation conversationNegotiation = Negotiation.start(Ids.negotiation(60L), couple,initialProposal, Clock.systemUTC());
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(Ids.conversation(321L), couple, conversationNegotiation);
		
		TimeConstraint Constraint = new TimeConstraint(LocalTime.of(22, 0));
				
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, Constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne);
		constraintChangeRequest.approve(partnerTwo);
		
		conversation.applyConstraints(constraintChangeRequest);
		
		Message message = new Message(Ids.message(110L),partnerOne);
		
		
		Paragraph paragraph = new SimpleParagraph(Ids.paragraph(1L), "title", "subtitle", "body");
		
		message.addParagraph(paragraph);
		
		DiscussionId discussionId = conversation.getDiscussions().getFirst().getId();
		
		conversation.sendMessage(message, discussionId, new ConstraintContext(LocalTime.of(23, 0)));
		
		Message secondMessage = new Message(Ids.message(120L),partnerOne);

		secondMessage.addParagraph(new SimpleParagraph(Ids.paragraph(2L), "title", "subtitle", "body"));
		
		// The second message is valid and satisfies the constraint, so only the turn-taking rule can reject it.
		DomainException exception = assertThrows(DomainException.class, ()-> conversation.sendMessage(secondMessage, discussionId, new ConstraintContext(LocalTime.of(23, 0))));

		assertEquals("A new message can't be sent if an answer wasn't received.", exception.getMessage());
		
	}
	
	
	@Test
	void shouldNotSendEmptyMessage()
	{
		UserId partnerOne = Ids.user(10L);
	    UserId partnerTwo = Ids.user(20L);
	    UserId therapist = Ids.user(30L);

	    Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = new AgreementProposal(Ids.proposal(123L), couple.getPartnerOneId(), "TEXT");
	    
	    Negotiation conversationNegotiation = Negotiation.start(Ids.negotiation(60L), couple,initialProposal, Clock.systemUTC());
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(Ids.conversation(321L), couple, conversationNegotiation);
		
		TimeConstraint Constraint = new TimeConstraint(LocalTime.of(22, 0));
				
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, Constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne);
		constraintChangeRequest.approve(partnerTwo);
		
		conversation.applyConstraints(constraintChangeRequest);
		
		Message message = new Message(Ids.message(110L), partnerOne);
		
		DiscussionId discussionId = conversation.getDiscussions().getFirst().getId();
		
		  assertThrows(DomainException.class,
				  () -> conversation.sendMessage(
		                    message,
		                    discussionId,
		                    new ConstraintContext(LocalTime.of(23, 0))
		            )
		    );
	}
	
}

