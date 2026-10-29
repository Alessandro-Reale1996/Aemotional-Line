package com.aemotionalline.domain.negotiation;

/**
 * Outcome of the request to talk. Independent from {@link ProposalStatus}, which tracks the agreement text.
 * REFUSED (the other partner refused the request) and ABANDONED (a partner gave up before an agreement) are final.
 */
public enum NegotiationStatus 
{
	DRAFT,
    ACCEPTED,
    REFUSED,
    ABANDONED
}
