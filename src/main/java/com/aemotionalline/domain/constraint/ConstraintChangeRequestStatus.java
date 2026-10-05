package com.aemotionalline.domain.constraint;

/** A request starts PENDING and ends either REJECTED or APPLIED; both are final, so a new request is needed to retry. */
public enum ConstraintChangeRequestStatus
{
	PENDING,
	REJECTED,
	APPLIED
}
