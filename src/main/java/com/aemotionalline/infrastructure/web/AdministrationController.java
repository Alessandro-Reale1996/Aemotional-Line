package com.aemotionalline.infrastructure.web;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.CoupleService;
import com.aemotionalline.application.UserService;
import com.aemotionalline.application.view.EndedCoupleView;
import com.aemotionalline.application.view.TherapistView;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.security.SessionUser;

/**
 * The administrator's work (CLAUDE.md, security point 3a and R4): the therapists' accounts, and the ended couples of a
 * disabled therapist, by id and date only. The whole path needs the role.
 */
@RestController
@RequestMapping("/api/admin")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AdministrationController
{
	record NewTherapist(String email, String displayName)
	{
		NewTherapist
		{
			Fields.required(email, "email");
			Fields.required(displayName, "displayName");
		}
	}

	private final UserService users;
	private final CoupleService couples;

	AdministrationController(UserService users, CoupleService couples)
	{
		this.users = users;
		this.couples = couples;
	}

	@GetMapping("/therapists")
	List<TherapistView> therapists(@AuthenticationPrincipal SessionUser administrator)
	{
		return users.therapists(administrator.userId());
	}

	/** The therapist receives the activation link by email. */
	@PostMapping("/therapists")
	ResponseEntity<Created<UserId>> createTherapist(@AuthenticationPrincipal SessionUser administrator, @RequestBody NewTherapist body)
	{
		UserId therapist = users.createTherapist(administrator.userId(), new EmailAddress(body.email()), body.displayName());

		return ResponseEntity.status(HttpStatus.CREATED).body(new Created<>(therapist));
	}

	/** A disabled therapist's ended couples, ids and end dates only: the ones whose data the administrator may delete. */
	@GetMapping("/therapists/{id}/ended-couples")
	List<EndedCoupleView> endedCouples(@AuthenticationPrincipal SessionUser administrator, @PathVariable UserId id)
	{
		return couples.endedCouplesOfDisabledTherapist(administrator.userId(), id);
	}

	/** Irreversible: the administrator's password confirms it. The therapist's couples end, their invitations are withdrawn. */
	@PostMapping("/therapists/{id}/disable")
	ResponseEntity<Void> disableTherapist(@AuthenticationPrincipal SessionUser administrator, @PathVariable UserId id,
			@RequestBody PasswordConfirmation body)
	{
		users.disableTherapist(administrator.userId(), id, body.password());

		return ResponseEntity.noContent().build();
	}
}
