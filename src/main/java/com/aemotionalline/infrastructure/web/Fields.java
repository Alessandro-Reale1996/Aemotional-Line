package com.aemotionalline.infrastructure.web;

import com.aemotionalline.application.InvalidRequestException;

/**
 * The shape checks of request bodies (CLAUDE.md, REST point 4): a required field that is missing is a malformed request,
 * 400 MALFORMED_REQUEST naming the field. Its content is the domain's business.
 */
final class Fields
{
	private Fields()
	{
	}

	static <T> T required(T value, String name)
	{
		if (value == null)
		{
			throw new InvalidRequestException("The field '" + name + "' is required.");
		}

		return value;
	}
}
