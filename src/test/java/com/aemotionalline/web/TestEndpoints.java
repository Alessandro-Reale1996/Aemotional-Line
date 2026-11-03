package com.aemotionalline.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.IncorrectPasswordException;
import com.aemotionalline.application.InvalidRequestException;
import com.aemotionalline.application.NotFoundException;
import com.aemotionalline.application.RequestErrorCode;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.UserId;

/**
 * Endpoints that exist only in the HTTP tests (imported where needed, never scanned): one under /api/admin for the
 * path rules, one throwing each kind of failure, one echoing the JSON shape of ids, instants and empty values.
 */
@TestComponent
@RestController
public class TestEndpoints
{
	public record EchoRequest(UserId user, Instant at, String text)
	{
		public EchoRequest
		{
			if (text == null)
			{
				throw new InvalidRequestException("The field 'text' is required.");
			}
		}
	}

	public record EchoView(UserId user, ConversationId conversation, DiscussionId discussion, Instant at, Optional<Instant> never, List<String> nothing,
			Role role, String text)
	{
	}

	@GetMapping("/api/admin/probe")
	String administrator()
	{
		return "administrator";
	}

	/** Each kind of failure; any other kind is an unexpected one, whose message (the kind) must not reach the log. */
	@GetMapping("/api/test/errors/{kind}")
	String failure(@PathVariable String kind)
	{
		throw switch (kind)
		{
			case "rule" -> new DomainException(ErrorCode.NOT_YOUR_TURN, "It is not this user's turn");
			case "guard" -> new DomainException("A guard.");
			case "domain-not-found" -> new DomainException(ErrorCode.NOT_FOUND, "Discussion not found");
			case "not-found" -> new NotFoundException("Couple not found.");
			case "invalid" -> new InvalidRequestException(RequestErrorCode.PASSWORD_TOO_SHORT, "The password must be at least 12 characters long.");
			case "password" -> new IncorrectPasswordException();
			default -> new IllegalStateException("Unexpected failure about " + kind);
		};
	}

	@GetMapping("/api/test/conversations/{id}")
	ConversationId conversation(@PathVariable ConversationId id)
	{
		return id;
	}

	@PostMapping("/api/test/echo")
	EchoView echo(@RequestBody EchoRequest request)
	{
		ConversationId conversation = ConversationId.random();

		return new EchoView(request.user(), conversation, new DiscussionId(conversation, 2), request.at(), Optional.empty(), List.of(), Role.PATIENT,
				request.text());
	}
}
