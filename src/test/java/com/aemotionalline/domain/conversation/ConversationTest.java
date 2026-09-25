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
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

public class ConversationTest
{
	
	
	@Test
	void shouldCreateConversationWithEmptyConstraintSet()
	{
	    UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = Proposal.create(new ProposalId(123L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
	    
	    Negotiation negotiation = Negotiation.start(new NegotiationId(60L), couple,initialProposal);
	    
	    negotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    negotiation.sendProposal(couple.getPartnerOneId());
	    
	    negotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(321L, couple, negotiation);

	    assertNotNull(conversation.getConstraintSet());
	}
	

	
	@Test
	void shouldNotAllowTherapistToSendMessageAsPartner()
	{
		
		UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = Proposal.create(new ProposalId(123L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
	    
	    Negotiation negotiation = Negotiation.start(new NegotiationId(60L), couple,initialProposal);
	    
	    negotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    negotiation.sendProposal(couple.getPartnerOneId());
	    
	    negotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(321L, couple, negotiation);
		
		assertFalse(conversation.canSendMessage(therapist));
			
	}
	
	
	
	@Test
	void shouldAddDiscussionToConversation()
	{
		UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = Proposal.create(new ProposalId(123L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
	    
	    Negotiation conversationNegotiation = Negotiation.start(new NegotiationId(60L), couple,initialProposal);
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId());
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(321L, couple, conversationNegotiation);
	 
	    Negotiation discussionNegotiation = Negotiation.start(new NegotiationId(70L), couple,initialProposal);
	    
	    discussionNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    discussionNegotiation.sendProposal(couple.getPartnerOneId());
	    
	    discussionNegotiation.acceptProposal(couple.getPartnerTwoId());
		
		conversation.openDiscussion(discussionNegotiation);
		
		assertEquals(2, conversation.getDiscussions().size());
	}
	

	
	
	@Test
	void shouldThrowExceptionWhenSendingIsNotAllowed()
	{
		UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = Proposal.create(new ProposalId(123L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
	    
	    Negotiation conversationNegotiation = Negotiation.start(new NegotiationId(60L), couple,initialProposal);
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId());
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(321L, couple, conversationNegotiation);
		
		assertThrows(DomainException.class, () -> conversation.ensureCanSendMessage(partnerOne));
	}
	
	@Test
	void shouldThrowExceptionWhenConstrictionsAreNotFullApproved()
	{
		UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = Proposal.create(new ProposalId(123L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
	    
	    Negotiation conversationNegotiation = Negotiation.start(new NegotiationId(60L), couple,initialProposal);
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId());
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(321L, couple, conversationNegotiation);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		
		assertThrows(DomainException.class, () -> conversation.applyConstraints(constraintChangeRequest));
	}
	
	@Test
	void shouldBlockMessageWhenConstraintFails()
	{
		UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = Proposal.create(new ProposalId(123L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
	    
	    Negotiation conversationNegotiation = Negotiation.start(new NegotiationId(60L), couple,initialProposal);
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId());
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(321L, couple, conversationNegotiation);
		
		TimeConstraint failConstraint = new TimeConstraint(LocalTime.of(23, 0));

		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, failConstraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		constraintChangeRequest.approve(partnerTwo, couple);
		
		conversation.applyConstraints(constraintChangeRequest);
		
		Message message = new Message(new MessageId(110L),partnerOne);
		
		DiscussionId discussionId = conversation.getDiscussions().getFirst().getId();
		
		  assertThrows( DomainException.class, () -> conversation.sendMessage(message, discussionId, new ConstraintContext(LocalTime.of(22, 0))));
	}
	
	@Test
	void shouldAddMessageToConversation()
	{
		UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = Proposal.create(new ProposalId(123L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
	    
	    Negotiation conversationNegotiation = Negotiation.start(new NegotiationId(60L), couple,initialProposal);
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId());
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(321L, couple, conversationNegotiation);
		
		TimeConstraint Constraint = new TimeConstraint(LocalTime.of(22, 0));
				
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, Constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		constraintChangeRequest.approve(partnerTwo, couple);
		
		conversation.applyConstraints(constraintChangeRequest);
		
		Message message = new Message(new MessageId(110L),partnerOne);
		
		
		Paragraph paragraph = new SimpleParagraph(new ParagraphId(1L), ParagraphType.SIMPLE, "title", "subtitle", "body");
		
		message.addParagraph(paragraph);
		
		Discussion discussion = conversation.getDiscussions().getFirst();
		
		conversation.sendMessage(message, discussion.getId(), new ConstraintContext(LocalTime.of(23, 0)));
		
	    assertEquals(message, conversation.findDiscussion(discussion.getId()).getMessages().getFirst());
	}
	
	@Test
	void shouldthrowExceptionWhenSenderIsTheSameAsLastSender()
	{
		UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = Proposal.create(new ProposalId(123L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
	    
	    Negotiation conversationNegotiation = Negotiation.start(new NegotiationId(60L), couple,initialProposal);
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId());
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(321L, couple, conversationNegotiation);
		
		TimeConstraint Constraint = new TimeConstraint(LocalTime.of(22, 0));
				
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, Constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		constraintChangeRequest.approve(partnerTwo, couple);
		
		conversation.applyConstraints(constraintChangeRequest);
		
		Message message = new Message(new MessageId(110L),partnerOne);
		
		
		Paragraph paragraph = new SimpleParagraph(new ParagraphId(1L), ParagraphType.SIMPLE, "title", "subtitle", "body");
		
		message.addParagraph(paragraph);
		
		DiscussionId discussionId = conversation.getDiscussions().getFirst().getId();
		
		conversation.sendMessage(message, discussionId, new ConstraintContext(LocalTime.of(23, 0)));
		
		Message secondMessage = new Message(new MessageId(120L),partnerOne);
		
		assertThrows(DomainException.class, ()-> conversation.sendMessage(secondMessage, discussionId, new ConstraintContext(LocalTime.of(23, 0))));
		
	}
	
	
	@Test
	void shouldNotSendEmptyMessage()
	{
		UserId partnerOne = new UserId(10L);
	    UserId partnerTwo = new UserId(20L);
	    UserId therapist = new UserId(30L);

	    Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
	    
	    Proposal initialProposal = Proposal.create(new ProposalId(123L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
	    
	    Negotiation conversationNegotiation = Negotiation.start(new NegotiationId(60L), couple,initialProposal);
	    
	    conversationNegotiation.acceptNegotiation(couple.getPartnerTwoId());
	    
	    conversationNegotiation.sendProposal(couple.getPartnerOneId());
	    
	    conversationNegotiation.acceptProposal(couple.getPartnerTwoId());

	    Conversation conversation = Conversation.start(321L, couple, conversationNegotiation);
		
		TimeConstraint Constraint = new TimeConstraint(LocalTime.of(22, 0));
				
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, Constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		constraintChangeRequest.approve(partnerTwo, couple);
		
		conversation.applyConstraints(constraintChangeRequest);
		
		Message message = new Message(new MessageId(110L), partnerOne);
		
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

