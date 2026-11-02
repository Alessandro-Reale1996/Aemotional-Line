package com.aemotionalline.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.aemotionalline.domain.user.EmailAddress;

/**
 * The rules a new password must follow (CLAUDE.md, security point 6d): at least 12 characters and no composition rules,
 * as current guidance prefers length; at most 64 characters and 72 bytes, the limit of bcrypt; not a common password
 * (list in common-passwords.txt, compared ignoring case) and not the account's email.
 */
final class PasswordPolicy
{
	static final int MIN_LENGTH = 12;
	static final int MAX_LENGTH = 64;
	private static final int MAX_BYTES = 72;

	private static final Set<String> COMMON = load();

	private PasswordPolicy()
	{
	}

	static void check(String password, EmailAddress email)
	{
		if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH)
		{
			throw new InvalidRequestException("The password must be at least " + MIN_LENGTH + " characters long.");
		}

		if (password.codePointCount(0, password.length()) > MAX_LENGTH || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
		{
			throw new InvalidRequestException("The password must be at most " + MAX_LENGTH + " characters long.");
		}

		String lower = password.toLowerCase(Locale.ROOT);

		if (COMMON.contains(lower) || lower.equals(email.value()))
		{
			throw new InvalidRequestException("This password is too easy to guess: choose another one.");
		}
	}

	private static Set<String> load()
	{
		try (InputStream in = PasswordPolicy.class.getResourceAsStream("/common-passwords.txt"))
		{
			if (in == null)
			{
				throw new IllegalStateException("common-passwords.txt is missing from the classpath.");
			}

			return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
					.map(String::strip)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.map(line -> line.toLowerCase(Locale.ROOT))
					.collect(Collectors.toUnmodifiableSet());
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("Can't read common-passwords.txt.", failure);
		}
	}
}
