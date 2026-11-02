package com.aemotionalline.application;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.view.ConstraintsView;
import com.aemotionalline.application.view.Views;
import com.aemotionalline.domain.constraint.Constraint;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.ConstraintScope;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.constraint.ReplyDelayConstraint;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.user.UserId;

/**
 * The constraints (spec 1.3.2, the therapist's control panel): the therapist proposes a new set, both partners must approve
 * it before it applies, either may reject it, the therapist may withdraw it. Every member sees the constraints and every
 * request, since both partners must approve each change.
 */
@Service
@Transactional
public class ConstraintService
{
	private final ConversationRepository conversations;

	ConstraintService(ConversationRepository conversations)
	{
		this.conversations = conversations;
	}

	/** The therapist proposes the whole new set of constraints (an empty list lifts them all). Returns the request's id. */
	public ConstraintChangeRequestId proposeConstraints(UserId therapist, ConversationId id, List<ConstraintSpec> specs)
	{
		if (specs == null)
		{
			throw new InvalidRequestException("The constraints to propose are required (an empty list lifts them all).");
		}

		List<ConstraintAssignment> assignments = specs.stream().map(ConstraintService::assignment).toList();

		Conversation conversation = ConversationAccess.locked(conversations, therapist, id);
		ConstraintChangeRequestId request = conversation.proposeConstraints(therapist, assignments);
		conversations.save(conversation);

		return request;
	}

	public void approveConstraints(UserId partner, ConversationId id, ConstraintChangeRequestId request)
	{
		change(partner, id, conversation -> conversation.approveConstraints(request, partner));
	}

	public void rejectConstraints(UserId partner, ConversationId id, ConstraintChangeRequestId request)
	{
		change(partner, id, conversation -> conversation.rejectConstraints(request, partner));
	}

	public void withdrawConstraints(UserId therapist, ConversationId id, ConstraintChangeRequestId request)
	{
		change(therapist, id, conversation -> conversation.withdrawConstraints(request, therapist));
	}

	@Transactional(readOnly = true)
	public ConstraintsView view(UserId viewer, ConversationId id)
	{
		return Views.constraints(ConversationAccess.readable(conversations, viewer, id));
	}

	private void change(UserId user, ConversationId id, Consumer<Conversation> change)
	{
		Conversation conversation = ConversationAccess.locked(conversations, user, id);
		change.accept(conversation);
		conversations.save(conversation);
	}

	private static ConstraintAssignment assignment(ConstraintSpec spec)
	{
		if (spec == null || spec.type() == null || spec.assignee() == null)
		{
			throw new InvalidRequestException("Every constraint needs a type and a partner it is assigned to.");
		}

		Constraint constraint = switch (spec.type())
		{
			case "TIME" -> new TimeConstraint(required(spec.notBefore(), "notBefore"), spec.scope() == null ? ConstraintScope.WRITE : spec.scope());
			case "REPLY_DELAY" ->
			{
				if (spec.scope() != null && spec.scope() != ConstraintScope.WRITE)
				{
					throw new InvalidRequestException("A reply delay always restricts writing.");
				}

				yield new ReplyDelayConstraint(required(spec.delay(), "delay"));
			}
			case "LOCATION" -> new LocationConstraint(new GeoPosition(required(spec.latitude(), "latitude"), required(spec.longitude(), "longitude")),
					required(spec.radiusMetres(), "radiusMetres"), required(spec.direction(), "direction"),
					spec.scope() == null ? ConstraintScope.READ : spec.scope());
			default -> throw new InvalidRequestException("Unknown constraint type: " + spec.type());
		};

		return new ConstraintAssignment(spec.assignee(), constraint);
	}

	private static <T> T required(T value, String name)
	{
		if (value == null)
		{
			throw new InvalidRequestException("The constraint's " + name + " is required.");
		}

		return value;
	}
}
