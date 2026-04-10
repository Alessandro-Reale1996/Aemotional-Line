package com.aemotionalline.domain.user;

public class UserId
{
	private Long value;

	public UserId(Long value)
	{
		if (value == null)
		{
			throw new IllegalArgumentException("User id cannot be null");
		}
		super();
		this.value = value;
	}
	
	public Long getId()
	{
		return this.value;
	}
	
}
