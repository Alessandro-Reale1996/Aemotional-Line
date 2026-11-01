package com.aemotionalline.application;

import java.io.Serial;

/**
 * The request is malformed (a blank search text, a reversed interval, an id in a message draft that isn't in the
 * conversation): not a broken domain rule, but something the client should have sent differently.
 */
public class InvalidRequestException extends RuntimeException
{
	@Serial
	private static final long serialVersionUID = 1L;

	public InvalidRequestException(String message)
	{
		super(message);
	}

	public InvalidRequestException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
