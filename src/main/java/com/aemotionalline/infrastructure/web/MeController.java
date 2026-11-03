package com.aemotionalline.infrastructure.web;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.HomeService;
import com.aemotionalline.application.view.CoupleView;
import com.aemotionalline.application.view.SummaryView;
import com.aemotionalline.infrastructure.security.SessionUser;

/** Where the frontend starts after the login (CLAUDE.md, R3): the user's couples and what waits for them. */
@RestController
@RequestMapping("/api/me")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class MeController
{
	private final HomeService home;

	MeController(HomeService home)
	{
		this.home = home;
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
}
