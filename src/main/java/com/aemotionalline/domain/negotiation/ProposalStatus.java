package com.aemotionalline.domain.negotiation;

/** Lifecycle of a single proposal: it is a draft until sent, then waits for the other partner's answer. */
public enum ProposalStatus 
{
	DRAFT,
	WAITING_FOR_RESPONSE,
	REFUSED,
	ACCEPTED
}
