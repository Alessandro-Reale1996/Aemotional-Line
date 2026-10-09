package com.aemotionalline.domain.negotiation;

/**
 * What the partners negotiate in a {@link Proposal}. Each kind of proposal accepts only its own kind of content,
 * so {@link Negotiation} can edit and copy a proposal without knowing what it holds.
 */
public sealed interface ProposalContent permits AgreementText, DiscussionTitle
{
}
