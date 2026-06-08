package com.aemotionalline.domain.conversation;

import java.util.Objects;

public record DiscussionId(Long value)
{
	public DiscussionId
	{	
		Objects.requireNonNull(value);
	}
	
}
