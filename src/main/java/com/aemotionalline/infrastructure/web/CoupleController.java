package com.aemotionalline.infrastructure.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.CoupleService;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.infrastructure.security.SessionUser;

/** The end of a couple and the deletion of its data (CLAUDE.md, S3 part 2): irreversible, confirmed with the password. */
@RestController
@RequestMapping("/api/couples/{id}")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CoupleController
{
	private final CoupleService couples;

	CoupleController(CoupleService couples)
	{
		this.couples = couples;
	}

	/** The therapist ends the couple: its conversation becomes read-only, everything pending stays frozen. */
	@PostMapping("/end")
	ResponseEntity<Void> end(@AuthenticationPrincipal SessionUser therapist, @PathVariable CoupleId id, @RequestBody PasswordConfirmation body)
	{
		couples.end(therapist.userId(), id, body.password());

		return ResponseEntity.noContent().build();
	}

	/** The therapist (or an administrator, the therapist's account being disabled) deletes every piece of the ended couple's data. */
	@PostMapping("/data-deletion")
	ResponseEntity<Void> deleteData(@AuthenticationPrincipal SessionUser user, @PathVariable CoupleId id, @RequestBody PasswordConfirmation body)
	{
		couples.deleteData(user.userId(), id, body.password());

		return ResponseEntity.noContent().build();
	}
}
