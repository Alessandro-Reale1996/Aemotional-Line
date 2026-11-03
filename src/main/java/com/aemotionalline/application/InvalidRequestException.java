package com.aemotionalline.application;

import java.io.Serial;
import java.util.Objects;

/**
 * The request is malformed (a blank search text, a reversed interval, an id in a message draft that isn't in the
 * conversation): not a broken domain rule, but something the client should have sent differently. Its code says which
 * case (MALFORMED_REQUEST unless given).
 */
public class InvalidRequestException extends RuntimeException
{
	@Serial
	private static final long serialVersionUID = 1L;

	private final RequestErrorCode code;

	public InvalidRequestException(String message)
	{
		this(RequestErrorCode.MALFORMED_REQUEST, message);
	}

	public InvalidRequestException(RequestErrorCode code, String message)
	{
		super(message);
		this.code = Objects.requireNonNull(code, "code");
	}

	public InvalidRequestException(String message, Throwable cause)
	{
		super(message, cause);
		this.code = RequestErrorCode.MALFORMED_REQUEST;
	}

	public RequestErrorCode getCode()
	{
		return code;
	}
}
