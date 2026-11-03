package com.aemotionalline.application;

import java.util.Objects;

import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.UserId;

/** Who just logged in: what the session needs to know about them (CLAUDE.md, security point 4). */
public record LoggedInUser(UserId id, Role role)
{
	public LoggedInUser
	{
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(role, "role");
	}
}
