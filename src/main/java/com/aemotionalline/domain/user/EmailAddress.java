package com.aemotionalline.domain.user;

import java.util.Locale;

import com.aemotionalline.domain.common.DomainException;

/** An account's email, which is also its login name: trimmed and lower-cased, so the same address can't be registered twice. */
public record EmailAddress(String value)
{
	private static final int MAX_LENGTH = 254;

	public EmailAddress
	{
		if (value == null || value.isBlank())
		{
			throw new DomainException("An email address is required.");
		}

		value = value.strip().toLowerCase(Locale.ROOT);

		int at = value.indexOf('@');

		if (value.length() > MAX_LENGTH || at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1 || value.chars().anyMatch(Character::isWhitespace))
		{
			throw new DomainException("Invalid email address.");
		}
	}
}
