package com.aemotionalline.application.view;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.negotiation.ProposalStatus;
import com.aemotionalline.domain.user.UserId;

/**
 * A proposal as the client sees it, flattened: kind AGREEMENT (text) or DISCUSSION_TITLE (title and subtitle).
 * justification and sentAt are null until it is sent.
 */
public record ProposalView(ProposalId id, UserId author, Kind kind, @Nullable String text, @Nullable String title, @Nullable String subtitle,
		@Nullable String justification, @Nullable Instant sentAt, ProposalStatus status)
{
	public enum Kind
	{
		AGREEMENT,
		DISCUSSION_TITLE
	}
}
