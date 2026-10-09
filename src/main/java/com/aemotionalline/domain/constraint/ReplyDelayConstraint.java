package com.aemotionalline.domain.constraint;

import java.time.Duration;

import com.aemotionalline.domain.common.DomainException;

/**
 * Forbids replying before a delay has passed since the message being answered was first read (spec 1.2:
 * "tra l'apertura di un messaggio e la possibilità di risposta deve passare un lasso di tempo previsto").
 * Without both instants in the context the constraint can't be shown satisfied, so it fails.
 */
public class ReplyDelayConstraint implements Constraint
{
	private final Duration delay;

	public ReplyDelayConstraint(Duration delay)
	{
		if (delay == null || delay.isNegative())
		{
			throw new DomainException("The delay can't be null or negative.");
		}

		this.delay = delay;
	}

	@Override
	public boolean isSatisfied(ConstraintContext context)
	{
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
