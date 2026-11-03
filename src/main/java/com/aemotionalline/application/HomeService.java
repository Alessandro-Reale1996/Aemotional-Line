package com.aemotionalline.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.aemotionalline.application.view.CoupleView;
import com.aemotionalline.application.view.PersonView;
import com.aemotionalline.application.view.SummaryView;
import com.aemotionalline.application.view.SummaryView.CoupleSummary;
import com.aemotionalline.application.view.SummaryView.NegotiationWaiting;
import com.aemotionalline.application.view.SummaryView.UnopenedMessages;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestStatus;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.Discussion;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.negotiation.NegotiationRepository;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * Where the frontend starts (CLAUDE.md, REST point 1 and R3): the user's couples and what waits for them. Read-only; it
 * only ever concerns the user's own couples, so there is nothing to hide from an outsider.
 */
@Service
@ReadOnlyTransaction
public class HomeService
{
	private final CoupleRepository couples;
	private final ConversationRepository conversations;
	private final NegotiationRepository negotiations;
	private final NegotiationPurposeRepository purposes;
	private final UserRepository users;
	private final InvitationService invitations;

	HomeService(CoupleRepository couples, ConversationRepository conversations, NegotiationRepository negotiations, NegotiationPurposeRepository purposes,
			UserRepository users, InvitationService invitations)
	{
		this.couples = couples;
		this.conversations = conversations;
		this.negotiations = negotiations;
		this.purposes = purposes;
		this.users = users;
		this.invitations = invitations;
	}

	/** The user's couples, as a partner or as the therapist: the active ones first. */
	public List<CoupleView> couples(UserId user)
	{
		return couples.findCouplesOf(user).stream()
				.map(couple -> new CoupleView(couple.getId(), person(couple.getPartnerOneId()), person(couple.getPartnerTwoId()), person(couple.getTherapistId()),
						couple.isActive(), couple.getEndedAt(), conversations.findIdByCouple(couple.getId())))
				.toList();
	}

	/** What waits for the user: the invitations to answer and, for each active couple, what is up to them. */
	public SummaryView summary(UserId user)
	{
		List<CoupleSummary> active = couples.findCouplesOf(user).stream().filter(Couple::isActive).map(couple -> summaryOf(couple, user)).toList();

		return new SummaryView(invitations.pending(user), active);
	}

	private CoupleSummary summaryOf(Couple couple, UserId user)
	{
		List<NegotiationWaiting> waiting = purposes.findInProgressOf(couple.getId()).stream()
				.filter(purpose -> negotiations.findById(purpose.negotiationId())
						.filter(negotiation -> !negotiation.isFailed() && user.equals(negotiation.getCurrentResponder())).isPresent())
				.map(purpose -> new NegotiationWaiting(purpose.negotiationId(), purpose.purpose()))
				.toList();
		Optional<ConversationId> conversationId = conversations.findIdByCouple(couple.getId());
		List<UnopenedMessages> unopened = new ArrayList<>();
		List<DiscussionId> closingRequests = new ArrayList<>();
		List<DiscussionId> therapistRequests = new ArrayList<>();
		List<ConstraintChangeRequestId> constraintRequests = new ArrayList<>();

		if (conversationId.isPresent())
		{
			Conversation conversation = conversations.findById(conversationId.get()).orElseThrow();

			for (Discussion discussion : conversation.getDiscussions())
			{
				if (couple.isPartner(user))
				{
					int count = (int) discussion.getMessages().stream()
							.filter(message -> !message.getSenderId().equals(user) && discussion.getReadAt(message.getId()).isEmpty())
							.count();

					if (count > 0)
					{
						unopened.add(new UnopenedMessages(discussion.getId(), count));
					}

					if (discussion.getClosingRequests().stream().anyMatch(request -> request.isPending() && !request.requester().equals(user)))
					{
						closingRequests.add(discussion.getId());
					}
				}
				else if (discussion.getTherapistClosingRequests().stream().anyMatch(request -> request.isPending()))
				{
					therapistRequests.add(discussion.getId());
				}
			}

			if (couple.isPartner(user))
			{
				conversation.getConstraintRequestHistory().stream()
						.filter(request -> request.status() == ConstraintChangeRequestStatus.PENDING && !request.approvals().contains(user))
						.forEach(request -> constraintRequests.add(request.id()));
			}
		}

		return new CoupleSummary(couple.getId(), conversationId, waiting, unopened, closingRequests, therapistRequests, constraintRequests);
	}

	private PersonView person(UserId id)
	{
		return new PersonView(id, users.findById(id).map(User::getDisplayName).orElse(null));
	}
}
