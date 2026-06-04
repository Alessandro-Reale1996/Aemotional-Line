package com.aemotionalline.domain.message;

import java.util.Objects;

public record MessageId(Long value)
{
	public MessageId
	{
		Objects.requireNonNull(value);
	}
}
