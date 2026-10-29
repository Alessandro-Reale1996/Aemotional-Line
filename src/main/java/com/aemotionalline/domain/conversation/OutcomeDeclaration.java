package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

/**
 * How one partner judges a closed specific discussion: its topic was resolved or it stalled, with a short reason.
 * Each partner declares their own, so the two may differ: the therapist sees both (spec 1.3.4: the archive shows how
 * a topic "sia stato risolto, o si sia arenato").
 */
public record OutcomeDeclaration(UserId partner, Outcome outcome, String reason, Instant declaredAt)
{
	public static final int MAX_REASON_LENGTH = 200;

	public enum Outcome
	{
		RESOLVED,
		STALLED
	}

	public OutcomeDeclaration
	{
		Objects.requireNonNull(partner);
		Objects.requireNonNull(declaredAt);

		if (outcome == null)
		{
			throw new DomainException("The outcome is required: resolved or stalled.");
		}

		if (reason == null || reason.isBlank())
		{
			throw new DomainException("The outcome needs a reason.");
		}

		if (reason.length() > MAX_REASON_LENGTH)
		{
			throw new DomainException("The reason can't be longer than " + MAX_REASON_LENGTH + " characters.");
		}
	}
}
