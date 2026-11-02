package com.aemotionalline.domain.user;

/**
 * What an account is, for good (a global role, not per couple): a therapist supervises couples and invites patients; a
 * patient is a partner in at most one active couple at a time; an administrator, the service operator, only creates and
 * disables therapist accounts and has no access to any couple's data.
 */
public enum Role
{
	THERAPIST,
	PATIENT,
	ADMINISTRATOR
}
