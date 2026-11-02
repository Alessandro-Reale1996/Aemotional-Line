package com.aemotionalline.domain.couple;

/**
 * PENDING until both partners accept (ACCEPTED: the couple exists); DECLINED by either partner, WITHDRAWN by the therapist,
 * EXPIRED when the time ran out. Every status but PENDING is final.
 */
public enum CoupleInvitationStatus
{
	PENDING,
	ACCEPTED,
	DECLINED,
	WITHDRAWN,
	EXPIRED
}
