package com.aemotionalline.infrastructure.web;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.InvalidRequestException;
import com.aemotionalline.application.NegotiationService;
import com.aemotionalline.application.view.NegotiationEffect;
import com.aemotionalline.application.view.NegotiationView;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalContent;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.infrastructure.security.SessionUser;

/**
 * The negotiations ("stipula"): the request, the exchange of proposals and the end, which takes effect at once
 * (CLAUDE.md, application point 4). The negotiation's kind is fixed at its start: a draft holds the same kind of content.
 */
@RestController
@RequestMapping("/api")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class NegotiationController
{
	record NewAgreementNegotiation(String text)
	{
		NewAgreementNegotiation
		{
			Fields.required(text, "text");
		}
	}

	record NewDiscussionNegotiation(String title, String subtitle, @Nullable ParagraphId originParagraphId)
	{
		NewDiscussionNegotiation
		{
			Fields.required(title, "title");
			Fields.required(subtitle, "subtitle");
		}
	}

	/** The new content of a draft: the agreement's text, or the title and subtitle of a discussion. */
	record DraftContent(@Nullable String text, @Nullable String title, @Nullable String subtitle)
	{
		DraftContent
		{
			if (text != null ? title != null || subtitle != null : title == null && subtitle == null)
			{
				throw new InvalidRequestException("Send either 'text', or 'title' and 'subtitle'.");
			}

			if (text == null)
			{
				Fields.required(title, "title");
				Fields.required(subtitle, "subtitle");
			}
		}

		ProposalContent content()
		{
			return text != null ? new AgreementText(text) : new DiscussionTitle(title, subtitle);
		}
	}

	record Sending(String justification)
	{
		Sending
		{
			Fields.required(justification, "justification");
		}
	}

	private final NegotiationService negotiations;

	NegotiationController(NegotiationService negotiations)
	{
		this.negotiations = negotiations;
	}

	// STARTING

	/** Starts the couple's conversation if it has none yet, otherwise changes its agreement. */
	@PostMapping("/couples/{id}/agreement-negotiations")
	@ResponseStatus(HttpStatus.CREATED)
	Created<NegotiationId> startAgreementNegotiation(@AuthenticationPrincipal SessionUser requester, @PathVariable CoupleId id,
			@RequestBody NewAgreementNegotiation body)
	{
		NegotiationId negotiation = negotiations.startAgreementNegotiation(requester.userId(), id, body.text());

		return new Created<>(negotiation);
	}

	/** Opens a specific discussion once agreed, optionally to expand a paragraph already sent. */
	@PostMapping("/conversations/{id}/discussion-negotiations")
	@ResponseStatus(HttpStatus.CREATED)
	Created<NegotiationId> startDiscussionNegotiation(@AuthenticationPrincipal SessionUser requester, @PathVariable ConversationId id,
			@RequestBody NewDiscussionNegotiation body)
	{
		NegotiationId negotiation = negotiations.startDiscussionNegotiation(requester.userId(), id, body.title(), body.subtitle(),
				Optional.ofNullable(body.originParagraphId()));

		return new Created<>(negotiation);
	}

	// READING

	/** The negotiation as this user sees it: an unsent draft is shown only to its author. */
	@GetMapping("/negotiations/{id}")
	NegotiationView view(@AuthenticationPrincipal SessionUser viewer, @PathVariable NegotiationId id)
	{
		return negotiations.view(viewer.userId(), id);
	}

	/** The couple's negotiations in progress, oldest first. */
	@GetMapping("/couples/{id}/negotiations")
	List<NegotiationView> inProgress(@AuthenticationPrincipal SessionUser viewer, @PathVariable CoupleId id)
	{
		return negotiations.inProgress(viewer.userId(), id);
	}

	/** The couple's failed attempts to start its conversation, kept here until the conversation starts. */
	@GetMapping("/couples/{id}/failed-starts")
	List<NegotiationView> failedStarts(@AuthenticationPrincipal SessionUser viewer, @PathVariable CoupleId id)
	{
		return negotiations.failedStarts(viewer.userId(), id);
	}

	// THE EXCHANGE

	@PostMapping("/negotiations/{id}/accept-request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void acceptRequest(@AuthenticationPrincipal SessionUser partner, @PathVariable NegotiationId id)
	{
		negotiations.acceptRequest(partner.userId(), id);
	}

	@PutMapping("/negotiations/{id}/draft")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void editDraft(@AuthenticationPrincipal SessionUser partner, @PathVariable NegotiationId id, @RequestBody DraftContent body)
	{
		negotiations.editProposal(partner.userId(), id, body.content());
	}

	@PostMapping("/negotiations/{id}/send")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void send(@AuthenticationPrincipal SessionUser partner, @PathVariable NegotiationId id, @RequestBody Sending body)
	{
		negotiations.sendProposal(partner.userId(), id, body.justification());
	}

	/** Refusing a proposal starts the refuser's counter-proposal: the answer is the id of the new draft. */
	@PostMapping("/negotiations/{id}/refuse-proposal")
	@ResponseStatus(HttpStatus.CREATED)
	Created<ProposalId> refuseProposal(@AuthenticationPrincipal SessionUser partner, @PathVariable NegotiationId id)
	{
		ProposalId draft = negotiations.refuseProposal(partner.userId(), id);

		return new Created<>(draft);
	}

	// THE END

	/** Takes effect at once: the answer says which conversation it started or changed, and the discussion it opened, if any. */
	@PostMapping("/negotiations/{id}/accept-proposal")
	NegotiationEffect acceptProposal(@AuthenticationPrincipal SessionUser partner, @PathVariable NegotiationId id)
	{
		return negotiations.acceptProposal(partner.userId(), id);
	}

	@PostMapping("/negotiations/{id}/refuse-request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void refuseRequest(@AuthenticationPrincipal SessionUser partner, @PathVariable NegotiationId id)
	{
		negotiations.refuseRequest(partner.userId(), id);
	}

	@PostMapping("/negotiations/{id}/abandon")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void abandon(@AuthenticationPrincipal SessionUser partner, @PathVariable NegotiationId id)
	{
		negotiations.abandon(partner.userId(), id);
	}
}
