package com.aemotionalline.domain.constraint;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.user.UserId;

/**
 * An immutable picture of a constraint change request, as the archive shows it (spec 1.3.2: the descriptive notifications
 * are tracked). It can't act on the request: only the conversation approves, rejects, withdraws or applies.
 * {@code previousAssignments} are the constraints the request replaced, present only once it was applied, so the archive
 * shows the constraints before and after each change.
 */
public record ConstraintRequestRecord(
		ConstraintChangeRequestId id,
		UserId proposedBy,
		List<ConstraintAssignment> assignments,
		ConstraintChangeRequestStatus status,
		Set<UserId> approvals,
		Instant proposedAt,
		Optional<Instant> closedAt,
		Optional<UserId> rejectedBy,
		Optional<List<ConstraintAssignment>> previousAssignments)
{
	public ConstraintRequestRecord
	{
		assignments = List.copyOf(assignments);
		approvals = Set.copyOf(approvals);
		previousAssignments = previousAssignments.map(List::copyOf);
	}
}
