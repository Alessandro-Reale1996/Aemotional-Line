package com.aemotionalline.domain.user;

/** PENDING until the owner activates the account through the one-time link sent to their email; DISABLED is final. */
public enum AccountStatus
{
	PENDING,
	ACTIVE,
	DISABLED
}
