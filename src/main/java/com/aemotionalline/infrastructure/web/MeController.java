package com.aemotionalline.infrastructure.web;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.HomeService;
import com.aemotionalline.application.UserService;
import com.aemotionalline.application.view.CoupleView;
import com.aemotionalline.application.view.SummaryView;
import com.aemotionalline.infrastructure.security.SessionUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** The user's own things (CLAUDE.md, R3 and R4): their couples, what waits for them, the deletion of their account. */
@RestController
@RequestMapping("/api/me")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class MeController
{
	private final HomeService home;
	private final UserService users;

	MeController(HomeService home, UserService users)
	{
		this.home = home;
		this.users = users;
	}

	@GetMapping("/couples")
	List<CoupleView> couples(@AuthenticationPrincipal SessionUser user)
	{
		return home.couples(user.userId());
	}

	@GetMapping("/summary")
	SummaryView summary(@AuthenticationPrincipal SessionUser user)
	{
		return home.summary(user.userId());
	}

	/**
	 * A patient deletes their own account, outside an active couple, confirming with their password; this session ends
	 * with the others, and the answer expires its cookie.
	 */
	@PostMapping("/account-deletion")
	ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal SessionUser user, @RequestBody PasswordConfirmation body, HttpServletRequest request)
	{
		users.deleteOwnPatientAccount(user.userId(), body.password());
		HttpSession session = request.getSession(false);

		if (session != null)
		{
			session.invalidate();
		}

		return ResponseEntity.noContent().build();
	}
}
