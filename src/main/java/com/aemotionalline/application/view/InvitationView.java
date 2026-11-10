package com.aemotionalline.application.view;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.couple.CoupleInvitationStatus;
import com.aemotionalline.domain.user.UserId;

/**
 * A couple invitation, as its therapist and its two invited partners see it: who invites whom, with their names, so that
 * the person invited knows who invites them and with whom before answering (CLAUDE.md, frontend point 5). An account
 * still waiting for activation carries the name the therapist entered.
 */
public record InvitationView(CoupleInvitationId id, PersonView therapist, PersonView partnerOne, PersonView partnerTwo, CoupleInvitationStatus status,
		Set<UserId> acceptedBy, Instant createdAt, Instant expiresAt, Optional<CoupleId> coupleId)
{
	public InvitationView
	{
		acceptedBy = Set.copyOf(acceptedBy);
	}
}
