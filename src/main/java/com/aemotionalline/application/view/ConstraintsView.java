package com.aemotionalline.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestStatus;
import com.aemotionalline.domain.user.UserId;

/** The constraints in force and every change request, in the order proposed (spec 1.3.2: the notifications are tracked). */
public record ConstraintsView(List<ConstraintView> inForce, List<RequestView> requests)
{
	public ConstraintsView
	{
		inForce = List.copyOf(inForce);
		requests = List.copyOf(requests);
	}

	/** A change request: what it proposed and, once applied, the constraints it replaced. */
	public record RequestView(ConstraintChangeRequestId id, UserId proposedBy, List<ConstraintView> assignments, ConstraintChangeRequestStatus status,
			Set<UserId> approvals, Instant proposedAt, Optional<Instant> closedAt, Optional<UserId> rejectedBy,
			Optional<List<ConstraintView>> previousAssignments)
	{
		public RequestView
		{
			assignments = List.copyOf(assignments);
			approvals = Set.copyOf(approvals);
		}
	}
}
