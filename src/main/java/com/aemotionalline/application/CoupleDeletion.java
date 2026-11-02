package com.aemotionalline.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.user.UserId;

/**
 * The minimal trace kept when a couple's data are deleted (CLAUDE.md, S3 part 2): which couple, its therapist, when it
 * ended, when its data were deleted and by whom. No partner ids and no content.
 */
public record CoupleDeletion(CoupleId couple, UserId therapist, Instant endedAt, Instant deletedAt, Cause cause, Optional<UserId> deletedBy)
{
	public enum Cause
	{
		/** The couple's therapist deleted the data. */
		THERAPIST,
		/** An administrator deleted the data, the therapist's account being disabled. */
		ADMINISTRATOR,
		/** The retention period after the end was over: nobody asked for it. */
		RETENTION
	}

	public CoupleDeletion
	{
		Objects.requireNonNull(couple, "couple");
		Objects.requireNonNull(therapist, "therapist");
		Objects.requireNonNull(endedAt, "endedAt");
		Objects.requireNonNull(deletedAt, "deletedAt");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(deletedBy, "deletedBy");

		if (deletedBy.isPresent() == (cause == Cause.RETENTION))
		{
			throw new IllegalArgumentException("Only a deletion by retention has no author.");
		}
	}
}
