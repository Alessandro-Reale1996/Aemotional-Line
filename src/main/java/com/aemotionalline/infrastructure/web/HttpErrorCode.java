package com.aemotionalline.infrastructure.web;

/**
 * The codes born in the HTTP layer (CLAUDE.md, R1 and R2), next to the domain's ErrorCode and the application's
 * RequestErrorCode: access refused, a failed login, an unexpected failure.
 */
public enum HttpErrorCode
{
	AUTHENTICATION_REQUIRED,
	INVALID_CREDENTIALS,
	/** A forbidden role, or a missing or wrong CSRF token. */
	ACCESS_DENIED,
	/** An unexpected failure: the answer carries an errorId, the same one the log has. */
	INTERNAL_ERROR
}
