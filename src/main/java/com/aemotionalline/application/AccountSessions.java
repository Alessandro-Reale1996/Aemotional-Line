package com.aemotionalline.application;

import com.aemotionalline.domain.user.UserId;

/** The login sessions of an account: disabling it or resetting its password ends all of them at once. */
public interface AccountSessions
{
	void endAllOf(UserId user);
}
