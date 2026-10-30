package com.aemotionalline.domain.negotiation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

/**
 * The stored facts of a negotiation in progress (or failed at couple level), from which the persistence layer rebuilds
 * it with {@link Negotiation#restore}. Every proposal is rebuilt with {@link Proposal#restore}.
 */
public record NegotiationSnapshot(
		NegotiationId id,
		Couple couple,
		NegotiationStatus status,
		UserId currentResponder,
		Optional<Proposal> currentProposal,
		Proposal request,
		List<Proposal> sentProposals,
		Instant startedAt,
		Optional<UserId> closedBy,
		Optional<Instant> closedAt)
{
	public NegotiationSnapshot
	{
		Objects.requireNonNull(id);
		Objects.requireNonNull(couple);
		Objects.requireNonNull(status);
		Objects.requireNonNull(currentResponder);
		Objects.requireNonNull(currentProposal);
		Objects.requireNonNull(request);
		Objects.requireNonNull(startedAt);
		Objects.requireNonNull(closedBy);
		Objects.requireNonNull(closedAt);
		sentProposals = List.copyOf(sentProposals);
	}
}
