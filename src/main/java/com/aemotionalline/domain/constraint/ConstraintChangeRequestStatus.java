package com.aemotionalline.domain.constraint;

/**
 * A request starts PENDING and ends REJECTED (by a partner), WITHDRAWN (by the therapist who proposed it) or APPLIED;
 * all three are final, so a new request is needed to retry.
 */
public enum ConstraintChangeRequestStatus
{
	PENDING,
	REJECTED,
	WITHDRAWN,
	APPLIED
}
