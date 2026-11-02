package com.aemotionalline.application.view;

import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.constraint.Constraint;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.constraint.ReplyDelayConstraint;
import com.aemotionalline.domain.constraint.TimeConstraint;
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
				.map(message -> message(message, canSeeContent(viewer, couple, discussion, message), discussion))
				.toList();

		return new DiscussionView(discussion.getId(), discussion.getTitle().map(title -> title.title()),
				discussion.getTitle().map(title -> title.subtitle()), discussion.getOriginParagraphId(), discussion.isClosed(),
				Optional.ofNullable(discussion.getLastSender()), messages);
	}

	/** One message as the viewer sees it (a closed envelope if the viewer may not see its content yet). */
	public static MessageView message(Message message, Discussion discussion, Couple couple, UserId viewer)
	{
		return message(message, canSeeContent(viewer, couple, discussion, message), discussion);
	}

	private static MessageView message(Message message, boolean visible, Discussion discussion)
	{
		List<ParagraphView> paragraphs = visible ? message.getParagraphs().stream().map(Views::paragraph).toList() : List.of();

		return new MessageView(message.getId(), message.getSenderId(), message.getSentAt(), !visible, discussion.getReadAt(message.getId()),
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
			case AgreementText text -> new ProposalView(proposal.getId(), proposal.getAuthor(), "AGREEMENT", text.text(), null, null,
					proposal.getJustification(), proposal.getSentAt(), proposal.getProposalStatus());
			case DiscussionTitle title -> new ProposalView(proposal.getId(), proposal.getAuthor(), "DISCUSSION_TITLE", null, title.title(),
					title.subtitle(), proposal.getJustification(), proposal.getSentAt(), proposal.getProposalStatus());
		};
	}

	// CLOSING

	public static ClosingView closing(Discussion discussion, Couple couple, UserId viewer)
	{
		boolean therapist = couple.isTherapist(viewer);

		List<ClosingView.TherapistRequestView> therapistRequests = discussion.getTherapistClosingRequests().stream()
				.map(request -> new ClosingView.TherapistRequestView(request.requester(),
						therapist || request.requester().equals(viewer) ? Optional.of(request.justification()) : Optional.empty(),
						request.sentAt(), request.previousRequests(), request.status(), request.decidedAt()))
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
				discussion.getClosingRequests(), therapistRequests, declarationViews);
	}

	// CONSTRAINTS

	public static ConstraintsView constraints(Conversation conversation)
	{
		List<ConstraintsView.RequestView> requests = conversation.getConstraintRequestHistory().stream()
				.map(request -> new ConstraintsView.RequestView(request.id(), request.proposedBy(), constraints(request.assignments()), request.status(),
						request.approvals(), request.proposedAt(), request.closedAt(), request.rejectedBy(),
						request.previousAssignments().map(Views::constraints)))
				.toList();

		return new ConstraintsView(constraints(conversation.getConstraintAssignments()), requests);
	}

	private static List<ConstraintView> constraints(List<ConstraintAssignment> assignments)
	{
		return assignments.stream().map(Views::constraint).toList();
	}

	private static ConstraintView constraint(ConstraintAssignment assignment)
	{
		Constraint constraint = assignment.getConstraint();

		return switch (constraint)
		{
			case TimeConstraint time -> new ConstraintView(assignment.getUserId(), "TIME", time.getScope(), time.getNotBefore(), null, null, null, null,
					null, time.getDescription());
			case ReplyDelayConstraint delay -> new ConstraintView(assignment.getUserId(), "REPLY_DELAY", delay.getScope(), null, delay.getDelay(), null,
					null, null, null, delay.getDescription());
			case LocationConstraint location -> new ConstraintView(assignment.getUserId(), "LOCATION", location.getScope(), null, null,
					location.getCenter().latitude(), location.getCenter().longitude(), location.getRadiusMetres(), location.getDirection(),
					location.getDescription());
			default -> new ConstraintView(assignment.getUserId(), constraint.getClass().getSimpleName(), constraint.getScope(), null, null, null, null,
					null, null, constraint.getDescription());
		};
	}

	public static ParagraphView paragraph(Paragraph paragraph)
	{
		return new ParagraphView(paragraph.getId(), paragraph.getType(), paragraph.getTitle(), paragraph.getSubtitle(), paragraph.getBody(),
				paragraph.getReferences().stream().map(Paragraph::getId).toList(),
				paragraph.getSelfCitations().stream().map(Paragraph::getId).toList());
	}
}
