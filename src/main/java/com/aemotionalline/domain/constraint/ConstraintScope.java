package com.aemotionalline.domain.constraint;

/** What a constraint restricts: reading a message, replying to it, or both (spec 1.3.2: "letti, o/e vi si potrà dare risposta"). */
public enum ConstraintScope
{
	READ,
	WRITE,
	BOTH;

	/** Whether a constraint of this scope applies when the given action (READ or WRITE) is performed. */
	public boolean covers(ConstraintScope action)
	{
		return this == BOTH || this == action;
	}
}
