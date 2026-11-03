package com.aemotionalline.infrastructure.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.AuthenticatedPrincipal;

import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.UserId;

/**
 * Who a login session belongs to: the account's id and role, nothing else (CLAUDE.md, S4). Its name is the id, never the
 * email, so the sessions table carries no personal data; it is also how all the sessions of an account are found.
 * Serializable, since Spring Session stores the security context in MariaDB.
 */
public record SessionUser(UUID id, Role role) implements AuthenticatedPrincipal, Serializable
{
	@Serial
	private static final long serialVersionUID = 1L;

	public SessionUser
	{
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(role, "role");
	}

	public UserId userId()
	{
		return new UserId(id);
	}

	@Override
	public String getName()
	{
		return id.toString();
	}

	static String nameOf(UserId user)
	{
		return user.value().toString();
	}
}
