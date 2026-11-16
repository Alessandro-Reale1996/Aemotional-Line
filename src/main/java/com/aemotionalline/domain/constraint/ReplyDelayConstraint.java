package com.aemotionalline.domain.constraint;

import java.time.Duration;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;

/**
 * Forbids replying before a delay has passed since the message being answered was first read (spec 1.2:
 * "tra l'apertura di un messaggio e la possibilità di risposta deve passare un lasso di tempo previsto").
 * A first message answers no message, so there is nothing to wait for (CLAUDE.md, F7); otherwise, without both instants
 * in the context the constraint can't be shown satisfied, so it fails.
 */
public class ReplyDelayConstraint implements Constraint
{
	public static final Duration MAX_DELAY = Duration.ofDays(30);

	private final Duration delay;

	public ReplyDelayConstraint(Duration delay)
	{
		if (delay == null || delay.isNegative())
		{
			throw new DomainException(ErrorCode.INVALID_CONSTRAINT, "The delay can't be null or negative.");
		}

		if (delay.compareTo(MAX_DELAY) > 0)
		{
			throw new DomainException(ErrorCode.INVALID_CONSTRAINT, "The delay can't be longer than " + MAX_DELAY.toDays() + " days.");
		}

		this.delay = delay;
	}

	public Duration getDelay()
	{
		return delay;
	}

	@Override
	public boolean isSatisfied(ConstraintContext context)
	{
		if (context.nothingToAnswer())
		{
			return true;
		}

		if (context.now() == null || context.readAt() == null)
		{
			return false;
		}

		// Negated isBefore makes the boundary inclusive: replying exactly after the delay is allowed.
		return !context.now().isBefore(context.readAt().plus(delay));
	}

	@Override
	public String getDescription()
	{
		return "Allowed " + delay + " after reading the message";
	}
}
