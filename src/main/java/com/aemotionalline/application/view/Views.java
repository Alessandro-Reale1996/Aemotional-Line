package com.aemotionalline.application.view;

import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.constraint.Constraint;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.constraint.ReplyDelayConstraint;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.conversation.ClosingRequestRecord;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.DiscussionClosure;
import com.aemotionalline.domain.conversation.OutcomeDeclaration;
import com.aemotionalline.domain.conversation.Discussion;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;

/**
 * Builds the views out of domain objects, inside the service's transaction, so that no domain object leaves the
 * application layer. It also applies the closed-envelope rule.
 */
public final class Views
{
	private Views()
	{
	}

	/**
	 * Whether the viewer may see a message's content: the therapist always, a partner their own messages, and the other
	 * partner's only once they have opened it (the read operation checked the read constraints). After that first opening
	 * the message is part of the freely consultable archive.
	 */
	public static boolean canSeeContent(UserId viewer, Couple couple, Discussion discussion, Message message)
	{
		return couple.isTherapist(viewer) || message.getSenderId().equals(viewer) || discussion.getReadAt(message.getId()).isPresent();
	}

	public static ConversationView conversation(Conversation conversation, UserId viewer)
	{
		String agreement = ((AgreementText) conversation.getAgreement().getContent()).text();

		List<DiscussionView> discussions = conversation.getDiscussions().stream()
				.map(discussion -> discussion(discussion, conversation.getCouple(), viewer))
				.toList();

		return new ConversationView(conversation.getId(), conversation.getCouple().getId(), agreement, discussions);
	}

	public static DiscussionView discussion(Discussion discussion, Couple couple, UserId viewer)
	{
		List<MessageView> messages = discussion.getMessages().stream()
				.map(message -> message(message, discussion, couple, viewer))
				.toList();

		return new DiscussionView(discussion.getId(), discussion.getTitle().map(title -> title.title()),
				discussion.getTitle().map(title -> title.subtitle()), discussion.getOriginParagraphId(), discussion.isClosed(),
				Optional.ofNullable(discussion.getLastSender()), messages);
	}

	/** One message as the viewer sees it (a closed envelope if the viewer may not see its content yet). */
	public static MessageView message(Message message, Discussion discussion, Couple couple, UserId viewer)
	{
		boolean visible = canSeeContent(viewer, couple, discussion, message);
		List<ParagraphView> paragraphs = visible ? message.getParagraphs().stream().map(Views::paragraph).toList() : List.of();

		return new MessageView(message.getId(), message.getSenderId(), message.getSentAt(), !visible,
				message.getSenderId().equals(viewer) ? Optional.empty() : discussion.getReadAt(message.getId()),
				paragraphs, visible ? message.getQuestionsLeftUnanswered() : List.of());
	}

	public static NegotiationView negotiation(Negotiation negotiation, NegotiationRecord.Purpose purpose, Optional<ParagraphId> origin, UserId viewer)
	{
		Proposal draft = negotiation.getCurrentProposal();
		Optional<ProposalView> draftView = draft != null && draft.getAuthor().equals(viewer) ? Optional.of(proposal(draft)) : Optional.empty();

		return new NegotiationView(negotiation.getId(), negotiation.getCouple().getId(), purpose, origin, negotiation.getNegotiationStatus(),
				negotiation.getCurrentResponder(), proposal(negotiation.getRequest()), negotiation.getProposals().stream().map(Views::proposal).toList(),
				draftView, negotiation.getStartedAt(), negotiation.getClosedBy(), negotiation.getClosedAt());
	}

	public static ProposalView proposal(Proposal proposal)
	{
		return switch (proposal.getContent())
		{
			case AgreementText text -> new ProposalView(proposal.getId(), proposal.getAuthor(), ProposalView.Kind.AGREEMENT, text.text(), null, null,
					proposal.getJustification(), proposal.getSentAt(), proposal.getProposalStatus());
			case DiscussionTitle title -> new ProposalView(proposal.getId(), proposal.getAuthor(), ProposalView.Kind.DISCUSSION_TITLE, null, title.title(),
					title.subtitle(), proposal.getJustification(), proposal.getSentAt(), proposal.getProposalStatus());
		};
	}

	// THE NEGOTIATIONS THAT ARE OVER

	public static NegotiationHistoryView negotiationHistory(Conversation conversation)
	{
		List<NegotiationHistoryView.TookEffect> tookEffect = conversation.getNegotiationHistory().stream()
				.map(record -> new NegotiationHistoryView.TookEffect(record.negotiationId(), record.purpose(), record.discussionId(), record.archivedAt(),
						record.proposals().stream().map(Views::proposal).toList()))
				.toList();

		List<NegotiationHistoryView.Failed> failed = conversation.getFailedNegotiations().stream()
				.map(record -> new NegotiationHistoryView.Failed(record.negotiationId(), record.attemptedPurpose(), record.outcome(), record.closedBy(),
						record.startedAt(), record.closedAt(), record.proposals().stream().map(Views::proposal).toList()))
				.toList();

		return new NegotiationHistoryView(tookEffect, failed);
	}

	// CLOSING

	public static ClosingView closing(Discussion discussion, Couple couple, UserId viewer)
	{
		boolean therapist = couple.isTherapist(viewer);

		List<ClosingView.TherapistRequestView> therapistRequests = discussion.getTherapistClosingRequests().stream()
				.map(request -> new ClosingView.TherapistRequestView(request.requester(),
						therapist || request.requester().equals(viewer) ? Optional.of(request.justification()) : Optional.empty(),
						request.sentAt(), closingRequests(request.previousRequests()), request.status(), request.decidedAt()))
				.toList();

		Optional<DiscussionClosure> closure = discussion.getClosure();
		List<OutcomeDeclaration> declarations = closure.map(DiscussionClosure::declarations).orElse(List.of());
		boolean bothDeclared = declarations.size() == 2;

		List<ClosingView.DeclarationView> declarationViews = declarations.stream()
				.map(declaration ->
				{
					boolean visible = therapist || bothDeclared || declaration.partner().equals(viewer);

					return new ClosingView.DeclarationView(declaration.partner(), visible ? Optional.of(declaration.outcome()) : Optional.empty(),
							visible ? Optional.of(declaration.reason()) : Optional.empty(), declaration.declaredAt());
				})
				.toList();

		return new ClosingView(discussion.getId(), discussion.isClosed(), closure.map(DiscussionClosure::way), closure.map(DiscussionClosure::closedAt),
				closingRequests(discussion.getClosingRequests()), therapistRequests, declarationViews);
	}

	private static List<ClosingView.ClosingRequestView> closingRequests(List<ClosingRequestRecord> requests)
	{
		return requests.stream()
				.map(request -> new ClosingView.ClosingRequestView(request.requester(), request.sentAt(), request.status(), request.endedAt()))
				.toList();
	}

	// CONSTRAINTS

	/**
	 * The constraints as one viewer sees them: the place of a location constraint (its centre) only for the therapist and the
	 * partner it binds, since it may be where that partner lives (CLAUDE.md, F9 decision 1); the other partner sees its
	 * radius and direction only.
	 */
	public static ConstraintsView constraints(Conversation conversation, UserId viewer)
	{
		Couple couple = conversation.getCouple();
		List<ConstraintsView.RequestView> requests = conversation.getConstraintRequestHistory().stream()
				.map(request -> new ConstraintsView.RequestView(request.id(), request.proposedBy(), constraints(request.assignments(), couple, viewer),
						request.status(), request.approvals(), request.proposedAt(), request.closedAt(), request.rejectedBy(),
						request.previousAssignments().map(previous -> constraints(previous, couple, viewer))))
				.toList();

		return new ConstraintsView(constraints(conversation.getConstraintAssignments(), couple, viewer), requests);
	}

	private static List<ConstraintView> constraints(List<ConstraintAssignment> assignments, Couple couple, UserId viewer)
	{
		return assignments.stream()
				.map(assignment -> constraint(assignment, couple.isTherapist(viewer) || assignment.getUserId().equals(viewer)))
				.toList();
	}

	private static ConstraintView constraint(ConstraintAssignment assignment, boolean seesThePlace)
	{
		Constraint constraint = assignment.getConstraint();

		return switch (constraint)
		{
			case TimeConstraint time -> new ConstraintView(assignment.getUserId(), ConstraintType.TIME, time.getScope(), time.getNotBefore(), null, null, null, null,
					null, time.getDescription());
			case ReplyDelayConstraint delay -> new ConstraintView(assignment.getUserId(), ConstraintType.REPLY_DELAY, delay.getScope(), null, delay.getDelay(), null,
					null, null, null, delay.getDescription());
			case LocationConstraint location when seesThePlace -> new ConstraintView(assignment.getUserId(), ConstraintType.LOCATION, location.getScope(), null,
					null, location.getCenter().latitude(), location.getCenter().longitude(), location.getRadiusMetres(), location.getDirection(),
					location.getDescription());
			// The domain's description names the centre too.
			case LocationConstraint location -> new ConstraintView(assignment.getUserId(), ConstraintType.LOCATION, location.getScope(), null, null, null,
					null, location.getRadiusMetres(), location.getDirection(),
					(location.getDirection() == LocationConstraint.Direction.WITHIN ? "Allowed within " : "Allowed beyond ") + location.getRadiusMetres()
							+ " m of the agreed place");
			default -> throw new IllegalStateException("No view for constraint type " + constraint.getClass().getName());
		};
	}

	public static ParagraphView paragraph(Paragraph paragraph)
	{
		return new ParagraphView(paragraph.getId(), paragraph.getType(), paragraph.getTitle(), paragraph.getSubtitle(), paragraph.getBody(),
				paragraph.getReferences().stream().map(Paragraph::getId).toList(),
				paragraph.getSelfCitations().stream().map(Paragraph::getId).toList());
	}
}
