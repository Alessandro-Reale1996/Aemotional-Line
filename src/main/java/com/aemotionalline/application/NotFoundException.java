package com.aemotionalline.application;

import java.io.Serial;

/**
 * The thing asked for doesn't exist, or the user acting doesn't belong to its couple: an outsider is told "not found",
 * never "forbidden", so the existence of a couple's conversation isn't revealed.
 */
public class NotFoundException extends RuntimeException
{
	@Serial
	private static final long serialVersionUID = 1L;

	public NotFoundException(String message)
	{
		super(message);
	}
}
