package com.aemotionalline.domain.negotiation;

/** Outcome of the request to talk. Independent from {@link ProposalStatus}, which tracks the agreement text. */
public enum NegotiationStatus 
{
	DRAFT,
    ACCEPTED,
    REFUSED
}
