package com.aemotionalline.domain.conversation;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class DiscussionResolutionRequestTest
{
	@Test
	void shouldThrowExceptionWhenRequesterIsNotPartner()
	{
		Couple couple = new Couple
					(
						1L,
						new UserId(10L), 
						new UserId(20L), 
						new UserId(30L)
					)
	}
}
