package com.aemotionalline.infrastructure.web;

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

import com.aemotionalline.application.ClosingService;
import com.aemotionalline.application.view.ClosingView;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.OutcomeDeclaration;
import com.aemotionalline.infrastructure.security.SessionUser;

/**
 * Closing a specific discussion: nobody closes it alone. A partner asks the other, or, after a failed attempt, asks the
 * therapist with a justification; once it is closed each partner declares whether its topic was resolved or stalled.
 */
@RestController
@RequestMapping("/api/conversations/{id}/discussions/{number}/closing")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ClosingController
{
	record TherapistRequest(String justification)
	{
		TherapistRequest
		{
			Fields.required(justification, "justification");
		}
	}

	record Declaration(OutcomeDeclaration.Outcome outcome, String reason)
	{
		Declaration
		{
			Fields.required(outcome, "outcome");
			Fields.required(reason, "reason");
		}
	}

	private final ClosingService closings;

	ClosingController(ClosingService closings)
	{
		this.closings = closings;
	}

	/** The closing as this user sees it: the justification of a special request and the other's declaration may be hidden. */
	@GetMapping
	ClosingView view(@AuthenticationPrincipal SessionUser viewer, @PathVariable ConversationId id, @PathVariable int number)
	{
		return closings.view(viewer.userId(), id, number);
	}

	// BETWEEN THE PARTNERS

	@PostMapping("/request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void request(@AuthenticationPrincipal SessionUser partner, @PathVariable ConversationId id, @PathVariable int number)
	{
		closings.requestClosing(partner.userId(), id, number);
	}

	/** The other partner accepts: the discussion closes at once, the declarations follow. */
	@PostMapping("/accept")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void accept(@AuthenticationPrincipal SessionUser partner, @PathVariable ConversationId id, @PathVariable int number)
	{
		closings.acceptClosing(partner.userId(), id, number);
	}

	@PostMapping("/refuse")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void refuse(@AuthenticationPrincipal SessionUser partner, @PathVariable ConversationId id, @PathVariable int number)
	{
		closings.refuseClosing(partner.userId(), id, number);
	}

	@PostMapping("/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@AuthenticationPrincipal SessionUser partner, @PathVariable ConversationId id, @PathVariable int number)
	{
		closings.withdrawClosing(partner.userId(), id, number);
	}

	// THROUGH THE THERAPIST

	/** Allowed only after a failed attempt with the other partner; the therapist sees the earlier requests with their instants. */
	@PostMapping("/therapist-request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void requestTherapist(@AuthenticationPrincipal SessionUser partner, @PathVariable ConversationId id, @PathVariable int number,
			@RequestBody TherapistRequest body)
	{
		closings.requestTherapistClosing(partner.userId(), id, number, body.justification());
	}

	@PostMapping("/therapist-request/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirmTherapistRequest(@AuthenticationPrincipal SessionUser therapist, @PathVariable ConversationId id, @PathVariable int number)
	{
		closings.confirmTherapistClosing(therapist.userId(), id, number);
	}

	@PostMapping("/therapist-request/refuse")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void refuseTherapistRequest(@AuthenticationPrincipal SessionUser therapist, @PathVariable ConversationId id, @PathVariable int number)
	{
		closings.refuseTherapistClosing(therapist.userId(), id, number);
	}

	// THE OUTCOME

	@PostMapping("/outcome")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void declareOutcome(@AuthenticationPrincipal SessionUser partner, @PathVariable ConversationId id, @PathVariable int number,
			@RequestBody Declaration body)
	{
		closings.declareOutcome(partner.userId(), id, number, body.outcome(), body.reason());
	}
}
