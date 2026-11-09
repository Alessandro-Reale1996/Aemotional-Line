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

import com.aemotionalline.application.InvitationService;
import com.aemotionalline.application.view.InvitationView;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.infrastructure.security.SessionUser;

/** How a couple is born (CLAUDE.md, security point 3): the therapist invites two patients, both accept. */
@RestController
@RequestMapping("/api/invitations")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class InvitationController
{
	record NewInvitation(String emailOne, String nameOne, String emailTwo, String nameTwo)
	{
		NewInvitation
		{
			Fields.required(emailOne, "emailOne");
			Fields.required(nameOne, "nameOne");
			Fields.required(emailTwo, "emailTwo");
			Fields.required(nameTwo, "nameTwo");
		}
	}

	private final InvitationService invitations;

	InvitationController(InvitationService invitations)
	{
		this.invitations = invitations;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Created<CoupleInvitationId> invite(@AuthenticationPrincipal SessionUser therapist, @RequestBody NewInvitation body)
	{
		CoupleInvitationId invitation = invitations.invite(therapist.userId(), new EmailAddress(body.emailOne()), body.nameOne(),
				new EmailAddress(body.emailTwo()), body.nameTwo());

		return new Created<>(invitation);
	}

	/** The user's invitations still waiting for an answer. */
	@GetMapping
	List<InvitationView> pending(@AuthenticationPrincipal SessionUser user)
	{
		return invitations.pending(user.userId());
	}

	@GetMapping("/{id}")
	InvitationView view(@AuthenticationPrincipal SessionUser user, @PathVariable CoupleInvitationId id)
	{
		return invitations.view(user.userId(), id);
	}

	@PostMapping("/{id}/accept")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void accept(@AuthenticationPrincipal SessionUser patient, @PathVariable CoupleInvitationId id)
	{
		invitations.accept(patient.userId(), id);
	}

	@PostMapping("/{id}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void decline(@AuthenticationPrincipal SessionUser patient, @PathVariable CoupleInvitationId id)
	{
		invitations.decline(patient.userId(), id);
	}

	@PostMapping("/{id}/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@AuthenticationPrincipal SessionUser therapist, @PathVariable CoupleInvitationId id)
	{
		invitations.withdraw(therapist.userId(), id);
	}
}
