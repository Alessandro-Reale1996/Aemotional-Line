package com.aemotionalline.application.view;

import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.negotiation.NegotiationId;

/**
 * What waits for the user when they come in (CLAUDE.md, REST point 1 and R3): the invitations to answer and, for each
 * active couple, what is up to them.
 */
public record SummaryView(List<InvitationView> pendingInvitations, List<CoupleSummary> couples)
{
	public SummaryView
	{
		pendingInvitations = List.copyOf(pendingInvitations);
		couples = List.copyOf(couples);
	}

	/**
	 * One active couple: the negotiations where it is the user's turn, the other partner's messages not yet opened per
	 * discussion, the closing requests to answer, the requests to the therapist to decide (therapist only), the constraint
	 * requests still waiting for the user's approval.
	 */
	public record CoupleSummary(CoupleId couple, Optional<ConversationId> conversation, List<NegotiationWaiting> negotiationsWaitingForYou,
			List<UnopenedMessages> unopenedMessages, List<DiscussionId> closingRequestsToAnswer, List<DiscussionId> therapistRequestsToDecide,
			List<ConstraintChangeRequestId> constraintRequestsToDecide)
	{
		public CoupleSummary
		{
			negotiationsWaitingForYou = List.copyOf(negotiationsWaitingForYou);
			unopenedMessages = List.copyOf(unopenedMessages);
			closingRequestsToAnswer = List.copyOf(closingRequestsToAnswer);
			therapistRequestsToDecide = List.copyOf(therapistRequestsToDecide);
			constraintRequestsToDecide = List.copyOf(constraintRequestsToDecide);
		}
	}

	public record NegotiationWaiting(NegotiationId id, NegotiationRecord.Purpose purpose)
	{
	}

	public record UnopenedMessages(DiscussionId discussion, int count)
	{
	}
}
