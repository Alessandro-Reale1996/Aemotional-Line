package com.aemotionalline.domain.user;

import com.aemotionalline.domain.common.DomainException;

/**
 * The stored form of a password, computed outside the domain (by the security infrastructure, with a slow modern
 * algorithm). The domain never sees a password in clear. toString doesn't reveal the hash.
 */
public record PasswordHash(String value)
{
	public PasswordHash
	{
		if (value == null || value.isBlank())
		{
			throw new DomainException("A password hash is required.");
		}
	}

	@Override
	public String toString()
	{
		return "PasswordHash[***]";
	}
}
