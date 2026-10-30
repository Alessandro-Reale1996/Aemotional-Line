package com.aemotionalline.domain.negotiation;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Test helper that plays the persistence layer for negotiations: it reads a negotiation's facts through its getters and
 * rebuilds every proposal from them, so a restored negotiation shares no object with the original. A fingerprint lists
 * every observable fact, to compare an original with its restored copy.
 */
public final class StoredFacts
{
	private StoredFacts()
	{
	}

	public static Proposal copy(Proposal proposal)
	{
		return Proposal.restore(proposal.getId(), proposal.getAuthor(), proposal.getContent(), proposal.getJustification(),
				proposal.getSentAt(), proposal.getProposalStatus());
	}

	public static List<Proposal> copy(List<Proposal> proposals)
	{
		return proposals.stream().map(StoredFacts::copy).toList();
	}

	public static NegotiationSnapshot snapshot(Negotiation negotiation)
	{
		return new NegotiationSnapshot(negotiation.getId(), negotiation.getCouple(), negotiation.getNegotiationStatus(),
				negotiation.getCurrentResponder(), Optional.ofNullable(negotiation.getCurrentProposal()).map(StoredFacts::copy),
				copy(negotiation.getRequest()), copy(negotiation.getProposals()), negotiation.getStartedAt(),
				negotiation.getClosedBy(), negotiation.getClosedAt());
	}

	public static String fingerprint(Proposal proposal)
	{
		return proposal == null ? "none" : proposal.getClass().getSimpleName() + "[" + proposal.getId() + ", " + proposal.getAuthor() + ", "
				+ proposal.getContent() + ", " + proposal.getJustification() + ", " + proposal.getSentAt() + ", " + proposal.getProposalStatus() + "]";
	}

	public static String fingerprint(List<Proposal> proposals)
	{
		return proposals.stream().map(StoredFacts::fingerprint).collect(Collectors.joining("; ", "[", "]"));
	}

	public static String fingerprint(Negotiation negotiation)
	{
		return String.join("\n",
				"id " + negotiation.getId(),
				"couple " + negotiation.getCouple().getId(),
				"status " + negotiation.getNegotiationStatus(),
				"responder " + negotiation.getCurrentResponder(),
				"draft " + fingerprint(negotiation.getCurrentProposal()),
				"request " + fingerprint(negotiation.getRequest()),
				"sent " + fingerprint(negotiation.getProposals()),
				"started " + negotiation.getStartedAt(),
				"closed " + negotiation.getClosedBy() + " " + negotiation.getClosedAt());
	}
}
