package com.aemotionalline.infrastructure.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.Limits;
import com.aemotionalline.application.view.LimitsView;

/** The text limits for the frontend's counters (CLAUDE.md, REST point 4): public, the password rule is needed before any login. */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class LimitsController
{
	@GetMapping("/api/limits")
	LimitsView limits()
	{
		return Limits.current();
	}
}
