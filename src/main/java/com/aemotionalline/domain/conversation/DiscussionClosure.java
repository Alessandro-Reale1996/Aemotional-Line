package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * How and when a specific discussion was closed, and the partners' declarations of its outcome so far (each partner
 * declares once, after the closure; a missing one is simply not in the list yet).
 */
public record DiscussionClosure(Way way, Instant closedAt, List<OutcomeDeclaration> declarations)
{
	public enum Way
	{
		/** The other partner accepted a closing request. */
		AGREEMENT,
		/** The therapist confirmed a partner's special request. */
		THERAPIST
	}

	public DiscussionClosure
	{
		Objects.requireNonNull(way);
		Objects.requireNonNull(closedAt);
		declarations = List.copyOf(declarations);
	}
}
