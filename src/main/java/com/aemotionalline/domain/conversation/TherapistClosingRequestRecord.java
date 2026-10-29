package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

/**
 * The "special" request to close a specific discussion without the other partner's consent: a partner who already
 * tried at least once, without success, asks the therapist, with a justification as long as a normal message. It
 * carries a picture of every closing request of the discussion up to then, with the instants they were sent, answered
 * or withdrawn, so the therapist sees how the partners got there.
 */
public record TherapistClosingRequestRecord(
		UserId requester,
		String justification,
		Instant sentAt,
		List<ClosingRequestRecord> previousRequests,
		Status status,
		Optional<Instant> decidedAt)
{
	// As long as the body of a simple paragraph, a "normal message".
	public static final int MAX_JUSTIFICATION_LENGTH = 800;

	public enum Status
	{
		PENDING,
		CONFIRMED,
		REFUSED
	}

	public TherapistClosingRequestRecord
	{
		Objects.requireNonNull(requester);
		Objects.requireNonNull(sentAt);
		Objects.requireNonNull(status);
		Objects.requireNonNull(decidedAt);
		previousRequests = List.copyOf(previousRequests);

		if (justification == null || justification.isBlank())
		{
			throw new DomainException("The request to the therapist needs a justification.");
		}

		if (justification.length() > MAX_JUSTIFICATION_LENGTH)
		{
			throw new DomainException("The justification can't be longer than " + MAX_JUSTIFICATION_LENGTH + " characters.");
		}
	}

	public boolean isPending()
	{
		return status == Status.PENDING;
	}

	TherapistClosingRequestRecord decide(Status decision, Instant at)
	{
		return new TherapistClosingRequestRecord(requester, justification, sentAt, previousRequests, decision, Optional.of(at));
	}
}
