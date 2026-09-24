package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

public class DiscussionTest
{
	@Test
	public void shouldAddMessageOnlyIfNotEmpty()
	{
		Message message = new Message(new MessageId(1L), new UserId(10L));
		
		Proposal proposal = Proposal.create(new ProposalId(0L), new UserId(10L), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
		
		Discussion discussion = new Discussion(new DiscussionId(50L, 60L), proposal);
		
		assertThrows(DomainException.class, ()-> discussion.addMessage(message));
	}
	
	@Test
	public void shouldThrowExceptionIfUserIdIsTheSameAsLAstSender()
	{
	
		Proposal proposal = Proposal.create(new ProposalId(0L), new UserId(10L), "TEXT", "JUSTIFICATION", Clock.systemDefaultZone());
		
		Discussion discussion = new Discussion(new DiscussionId(50L, 60L), proposal);
		
		discussion.setLastSender(new UserId(0L));
		
		assertThrows(DomainException.class, ()-> discussion.setLastSender(new UserId(0L)));
	}
	
}
