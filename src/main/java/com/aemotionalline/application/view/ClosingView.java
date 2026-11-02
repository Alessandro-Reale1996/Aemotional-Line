package com.aemotionalline.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.conversation.ClosingRequestRecord;
import com.aemotionalline.domain.conversation.DiscussionClosure;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.OutcomeDeclaration;
import com.aemotionalline.domain.conversation.TherapistClosingRequestRecord;
import com.aemotionalline.domain.user.UserId;

/**
 * The closing of a specific discussion as one viewer sees it: every request with its instants, the special requests to the
 * therapist (their justification only for the requester and the therapist), and the closure with the declarations (a
 * partner sees the other's only once both have declared).
 */
public record ClosingView(DiscussionId discussionId, boolean closed, Optional<DiscussionClosure.Way> way, Optional<Instant> closedAt,
		List<ClosingRequestRecord> requests, List<TherapistRequestView> therapistRequests, List<DeclarationView> declarations)
{
	public ClosingView
	{
		requests = List.copyOf(requests);
		therapistRequests = List.copyOf(therapistRequests);
		declarations = List.copyOf(declarations);
	}

	/** A special request: {@code justification} is empty for whoever may not read it. */
	public record TherapistRequestView(UserId requester, Optional<String> justification, Instant sentAt, List<ClosingRequestRecord> previousRequests,
			TherapistClosingRequestRecord.Status status, Optional<Instant> decidedAt)
	{
		public TherapistRequestView
		{
			previousRequests = List.copyOf(previousRequests);
		}
	}

	/** A partner's declaration: outcome and reason are empty while the viewer may not see them yet. */
	public record DeclarationView(UserId partner, Optional<OutcomeDeclaration.Outcome> outcome, Optional<String> reason, Instant declaredAt)
	{
	}
}
