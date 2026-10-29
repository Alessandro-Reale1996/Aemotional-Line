package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.user.UserId;

/**
 * A partner's request to close a specific discussion, as the archive and the therapist see it: who sent it, when, and
 * how and when it ended (accepted or refused by the other partner, or withdrawn by the requester). Nobody closes a
 * discussion alone: a refused or withdrawn request is a failed attempt, which is what allows asking the therapist.
 */
public record ClosingRequestRecord(UserId requester, Instant sentAt, Status status, Optional<Instant> endedAt)
{
	public enum Status
	{
		PENDING,
		ACCEPTED,
		REFUSED,
		WITHDRAWN
	}

	public ClosingRequestRecord
	{
		Objects.requireNonNull(requester);
		Objects.requireNonNull(sentAt);
		Objects.requireNonNull(status);
		Objects.requireNonNull(endedAt);
	}

	public boolean isPending()
	{
		return status == Status.PENDING;
	}

	/** Refused by the other partner, or withdrawn unanswered: the requester tried to close and couldn't. */
	public boolean isFailedAttempt()
	{
		return status == Status.REFUSED || status == Status.WITHDRAWN;
	}

	ClosingRequestRecord end(Status finalStatus, Instant at)
	{
		return new ClosingRequestRecord(requester, sentAt, finalStatus, Optional.of(at));
	}
}
