package com.aemotionalline.application;

/**
 * The stable codes of the application's own failures (CLAUDE.md, R1), next to the domain's ErrorCode: a request the
 * client should have sent differently, a bad one-time link, a password that breaks the policy, a wrong confirmation
 * password. A code, once published, keeps its name.
 */
public enum RequestErrorCode
{
	/** Malformed JSON, a missing or unknown field, an id in a draft that isn't in the conversation: a frontend error. */
	MALFORMED_REQUEST,
	INVALID_LINK,
	PASSWORD_TOO_SHORT,
	PASSWORD_TOO_LONG,
	/** A common password, or the account's email. */
	PASSWORD_TOO_EASY,
	/** The password confirming an irreversible action is wrong, or the account is locked. */
	INCORRECT_PASSWORD
}
