package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertThrows;


import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.Ids;

public class DiscussionTest
{
	@Test
	public void shouldAddMessageOnlyIfNotEmpty()
	{
		Message message = new Message(Ids.message(1L), Ids.user(10L));
		
		Discussion discussion = new Discussion(new DiscussionId(Ids.conversation(50L), 60), null);
		
		assertThrows(DomainException.class, ()-> discussion.addMessage(message));
	}
	
	@Test
	public void shouldThrowExceptionIfUserIdIsTheSameAsLastSender()
	{
	
		Discussion discussion = new Discussion(new DiscussionId(Ids.conversation(50L), 60), null);
		
		discussion.setLastSender(Ids.user(0L));
		
		assertThrows(DomainException.class, ()-> discussion.setLastSender(Ids.user(0L)));
	}
	
}
