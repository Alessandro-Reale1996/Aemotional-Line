package com.aemotionalline.application.view;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.couple.CoupleInvitationStatus;
import com.aemotionalline.domain.user.UserId;

/** A couple invitation, as its therapist and its two invited partners see it. */
public record InvitationView(CoupleInvitationId id, UserId therapist, UserId partnerOne, UserId partnerTwo, CoupleInvitationStatus status,
		Set<UserId> acceptedBy, Instant createdAt, Instant expiresAt, Optional<CoupleId> coupleId)
{
	public InvitationView
	{
		acceptedBy = Set.copyOf(acceptedBy);
	}
}
