package com.aemotionalline.infrastructure.security;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.domain.user.PasswordHash;

/**
 * Passwords hashed with Spring Security's delegating encoder: bcrypt today, and the stored "{id}" prefix lets the
 * algorithm change later without invalidating the existing hashes.
 */
@Component
public class SpringPasswordHasher implements PasswordHasher
{
	private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	@Override
	public PasswordHash hash(String password)
	{
		return new PasswordHash(encoder.encode(password));
	}

	@Override
	public boolean matches(String password, PasswordHash hash)
	{
		// bcrypt reads at most 72 bytes and recent encoders refuse longer input; no such password was ever accepted.
		return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72 && encoder.matches(password, hash.value());
	}
}
