package com.aemotionalline.domain.couple;

import java.util.Objects;
import java.util.UUID;

import com.aemotionalline.domain.common.UuidV7;

/** Identity of a couple invitation. */
public record CoupleInvitationId(UUID value)
{
	public CoupleInvitationId
	{
		Objects.requireNonNull(value, "CoupleInvitationId cannot be null.");
	}

	public static CoupleInvitationId random()
	{
		return new CoupleInvitationId(UuidV7.generate());
	}
}
