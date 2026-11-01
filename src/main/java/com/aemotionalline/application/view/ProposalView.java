package com.aemotionalline.application.view;

import java.time.Instant;

import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.negotiation.ProposalStatus;
import com.aemotionalline.domain.user.UserId;

/**
 * A proposal as the client sees it, flattened: kind AGREEMENT (text) or DISCUSSION_TITLE (title and subtitle).
 * justification and sentAt are null until it is sent.
 */
public record ProposalView(ProposalId id, UserId author, String kind, String text, String title, String subtitle, String justification,
		Instant sentAt, ProposalStatus status)
{
}
