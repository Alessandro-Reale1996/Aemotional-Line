package com.aemotionalline.application;

import java.io.Serial;

/**
 * The password given to confirm an irreversible operation is not the account's (or the account is locked after too many
 * failures). The failure is recorded on the account, so the transaction that throws it must still commit.
 */
public class IncorrectPasswordException extends InvalidRequestException
{
	@Serial
	private static final long serialVersionUID = 1L;

	public IncorrectPasswordException()
	{
		super(RequestErrorCode.INCORRECT_PASSWORD, "The password is not correct.");
	}
}
