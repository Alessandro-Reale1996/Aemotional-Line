package com.aemotionalline.infrastructure.web;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.ArchiveService;
import com.aemotionalline.application.view.ArchiveEntry;
import com.aemotionalline.application.view.ArchiveSearchResult;
import com.aemotionalline.application.view.NegotiationHistoryView;
import com.aemotionalline.application.view.TopicHistoryView;
import com.aemotionalline.application.view.UnansweredQuestionsView;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.infrastructure.security.SessionUser;

/**
 * The archive: everything a couple's members may consult at any time, also after the couple has ended. A partner never sees
 * the content of the other partner's messages they haven't opened.
 */
@RestController
@RequestMapping("/api/conversations/{id}/archive")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ArchiveController
{
	record Search(String text)
	{
		Search
		{
			Fields.required(text, "text");
		}
	}

	private final ArchiveService archive;

	ArchiveController(ArchiveService archive)
	{
		this.archive = archive;
	}

	/** A POST, though it changes nothing, so that the word searched, which can be very personal, stays out of every address and log. */
	@PostMapping("/search")
	ArchiveSearchResult search(@AuthenticationPrincipal SessionUser viewer, @PathVariable ConversationId id, @RequestBody Search body)
	{
		return archive.search(viewer.userId(), id, body.text());
	}

	/** The messages sent from {@code from} (included) to {@code to} (excluded); the unopened ones are closed envelopes. */
	@GetMapping("/messages")
	List<ArchiveEntry> messages(@AuthenticationPrincipal SessionUser viewer, @PathVariable ConversationId id, @RequestParam Instant from, @RequestParam Instant to)
	{
		return archive.messagesBetween(viewer.userId(), id, from, to);
	}

	@GetMapping("/topics/{paragraphId}")
	TopicHistoryView topic(@AuthenticationPrincipal SessionUser viewer, @PathVariable ConversationId id, @PathVariable ParagraphId paragraphId)
	{
		return archive.topicHistory(viewer.userId(), id, paragraphId);
	}

	@GetMapping("/unanswered-questions")
	UnansweredQuestionsView unansweredQuestions(@AuthenticationPrincipal SessionUser viewer, @PathVariable ConversationId id)
	{
		return archive.unansweredQuestions(viewer.userId(), id);
	}

	@GetMapping("/negotiations")
	NegotiationHistoryView negotiations(@AuthenticationPrincipal SessionUser viewer, @PathVariable ConversationId id)
	{
		return archive.negotiations(viewer.userId(), id);
	}
}
