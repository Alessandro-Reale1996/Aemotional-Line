package com.aemotionalline.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.user.UserId;

/**
 * A one-time link sent by email, to activate an account or to reset its password. Only a hash of its code is kept, so
 * reading the database gives no usable link; it works once and expires.
 */
public record OneTimeLink(String codeHash, UserId user, Purpose purpose, Instant createdAt, Instant expiresAt, Optional<Instant> usedAt)
{
	public enum Purpose
	{
		ACTIVATION,
		PASSWORD_RESET
	}

	public OneTimeLink
	{
		Objects.requireNonNull(codeHash);
		Objects.requireNonNull(user);
		Objects.requireNonNull(purpose);
		Objects.requireNonNull(createdAt);
		Objects.requireNonNull(expiresAt);
		Objects.requireNonNull(usedAt);
	}

	public boolean isUsableAt(Instant now)
	{
		return usedAt.isEmpty() && now.isBefore(expiresAt);
	}

	public OneTimeLink usedAt(Instant at)
	{
		return new OneTimeLink(codeHash, user, purpose, createdAt, expiresAt, Optional.of(at));
	}
}
