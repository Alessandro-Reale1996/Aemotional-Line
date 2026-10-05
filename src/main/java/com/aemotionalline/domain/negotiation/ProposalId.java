package com.aemotionalline.domain.negotiation;

import java.util.Objects;
import java.util.UUID;

/** Identity of a proposal, a type of its own so it cannot be confused with the other ids. */
public record ProposalId(UUID value)
{
	public ProposalId
	{
		Objects.requireNonNull(value, "ProposalId cannot be null.");
	}

	/** The domain creates its own ids, so an aggregate never has to wait for the database to have one. */
	public static ProposalId random()
	{
		return new ProposalId(UUID.randomUUID());
	}
}
