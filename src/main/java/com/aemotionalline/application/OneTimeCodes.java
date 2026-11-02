package com.aemotionalline.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The secret codes of the one-time links: 32 random bytes, URL-safe. Only their SHA-256 hash is stored, so the database
 * holds no usable link (a fast hash is enough: the codes are long and random, nothing to guess).
 */
final class OneTimeCodes
{
	private static final SecureRandom RANDOM = new SecureRandom();

	private OneTimeCodes()
	{
	}

	static String newCode()
	{
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);

		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String code)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));

			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException impossible)
		{
			throw new IllegalStateException("SHA-256 is always available.", impossible);
		}
	}
}
