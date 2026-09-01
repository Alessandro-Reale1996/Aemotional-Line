package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.user.UserId;

public class DiscussionTest
{
	@Test
	public void shouldAddMessageOnlyIfNotEmpty()
	{
		Message message = new Message(new MessageId(1L), new UserId(10L));
		
		Discussion discussion = new Discussion(new DiscussionId(50L));
		
		assertThrows(DomainException.class, ()-> discussion.addMessage(message));
	}
	
	@Test
	public void shouldThrowExceptionIfUserIdIsTheSameAsLAstSender()
	{
	
		Discussion discussion = new Discussion(new DiscussionId(50L));
		
		discussion.setLastSender(new UserId(0L));
		
		assertThrows(DomainException.class, ()-> discussion.setLastSender(new UserId(0L)));
	}
	
}
