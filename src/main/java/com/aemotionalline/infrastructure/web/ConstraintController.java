package com.aemotionalline.infrastructure.web;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.ConstraintService;
import com.aemotionalline.application.ConstraintSpec;
import com.aemotionalline.application.view.ConstraintsView;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.infrastructure.security.SessionUser;

/**
 * The constraints: the therapist proposes a whole new set, which takes effect when both partners have approved it. Every
 * member of the couple sees the constraints in force and every request.
 */
@RestController
@RequestMapping("/api/conversations/{id}/constraints")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ConstraintController
{
	record ProposedConstraints(List<ConstraintSpec> constraints)
	{
		ProposedConstraints
		{
			Fields.required(constraints, "constraints");
		}
	}

	private final ConstraintService constraints;

	ConstraintController(ConstraintService constraints)
	{
		this.constraints = constraints;
	}

	@GetMapping
	ConstraintsView view(@AuthenticationPrincipal SessionUser viewer, @PathVariable ConversationId id)
	{
		return constraints.view(viewer.userId(), id);
	}

	/** An empty list lifts every constraint. */
	@PostMapping("/requests")
	@ResponseStatus(HttpStatus.CREATED)
	Created<ConstraintChangeRequestId> propose(@AuthenticationPrincipal SessionUser therapist, @PathVariable ConversationId id,
			@RequestBody ProposedConstraints body)
	{
		ConstraintChangeRequestId request = constraints.proposeConstraints(therapist.userId(), id, body.constraints());

		return new Created<>(request);
	}

	/** The second approval applies the constraints at once. */
	@PostMapping("/requests/{requestId}/approve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void approve(@AuthenticationPrincipal SessionUser partner, @PathVariable ConversationId id, @PathVariable ConstraintChangeRequestId requestId)
	{
		constraints.approveConstraints(partner.userId(), id, requestId);
	}

	@PostMapping("/requests/{requestId}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reject(@AuthenticationPrincipal SessionUser partner, @PathVariable ConversationId id, @PathVariable ConstraintChangeRequestId requestId)
	{
		constraints.rejectConstraints(partner.userId(), id, requestId);
	}

	@PostMapping("/requests/{requestId}/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@AuthenticationPrincipal SessionUser therapist, @PathVariable ConversationId id, @PathVariable ConstraintChangeRequestId requestId)
	{
		constraints.withdrawConstraints(therapist.userId(), id, requestId);
	}
}
