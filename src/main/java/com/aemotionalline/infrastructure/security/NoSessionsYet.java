package com.aemotionalline.infrastructure.security;

import org.springframework.stereotype.Component;

import com.aemotionalline.application.AccountSessions;
import com.aemotionalline.domain.user.UserId;

/**
 * Temporary: there are no login sessions until Spring Security is configured (security step S4), which replaces this
 * with the implementation that ends an account's sessions stored in MariaDB.
 */
@Component
public class NoSessionsYet implements AccountSessions
{
	@Override
	public void endAllOf(UserId user)
	{
		// Nothing to end yet: logins don't exist before S4.
	}
}
