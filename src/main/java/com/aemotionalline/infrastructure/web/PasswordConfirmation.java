package com.aemotionalline.infrastructure.web;

/** The account's own password, confirming an irreversible action (CLAUDE.md, REST point 5). */
record PasswordConfirmation(String password)
{
	PasswordConfirmation
	{
		Fields.required(password, "password");
	}
}
