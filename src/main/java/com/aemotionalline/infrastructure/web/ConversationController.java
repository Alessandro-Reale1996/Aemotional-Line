package com.aemotionalline.infrastructure.web;

import java.time.LocalTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
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

import com.aemotionalline.application.ConversationService;
import com.aemotionalline.application.MessageDraft;
import com.aemotionalline.application.ParagraphDraft;
import com.aemotionalline.application.view.ConversationView;
import com.aemotionalline.application.view.MessagePreviewView;
import com.aemotionalline.application.view.MessageView;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.infrastructure.security.SessionUser;

/**
 * The conversation: reading it, opening a message, sending one and the previews for the pop-ups. The local time of day
 * and the position serve the constraints: both may be missing, and a constraint that needs one then refuses the action.
 * The position is not stored.
 */
@RestController
@RequestMapping("/api/conversations/{id}")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ConversationController
{
	record Position(Double latitude, Double longitude)
	{
		Position
		{
			Fields.required(latitude, "position.latitude");
			Fields.required(longitude, "position.longitude");
		}
	}

	record NewMessage(List<ParagraphDraft> paragraphs, @Nullable LocalTime localTime, @Nullable Position position)
	{
		NewMessage
		{
			Fields.required(paragraphs, "paragraphs");
		}
	}

	record Opening(@Nullable LocalTime localTime, @Nullable Position position)
	{
	}

	record DraftToPreview(List<ParagraphDraft> paragraphs)
	{
		DraftToPreview
		{
			Fields.required(paragraphs, "paragraphs");
		}
	}

	private final ConversationService conversations;

	ConversationController(ConversationService conversations)
	{
		this.conversations = conversations;
	}

	/** The conversation as this user sees it: the other partner's messages not opened yet are closed envelopes. */
	@GetMapping
	ConversationView view(@AuthenticationPrincipal SessionUser viewer, @PathVariable ConversationId id)
	{
		return conversations.view(viewer.userId(), id);
	}

	@PostMapping("/discussions/{number}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	Created<MessageId> send(@AuthenticationPrincipal SessionUser sender, @PathVariable ConversationId id, @PathVariable int number,
			@RequestBody NewMessage body)
	{
		MessageId message = conversations.sendMessage(sender.userId(), id, number, new MessageDraft(body.paragraphs()), body.localTime(),
				geoPosition(body.position()));

		return new Created<>(message);
	}

	/** Opens a message: the read constraints are checked and the first opening is recorded. The answer has its content. */
	@PostMapping("/discussions/{number}/messages/{messageId}/open")
	MessageView open(@AuthenticationPrincipal SessionUser reader, @PathVariable ConversationId id, @PathVariable int number,
			@PathVariable MessageId messageId, @RequestBody Opening body)
	{
		return conversations.readMessage(reader.userId(), id, number, messageId, body.localTime(), geoPosition(body.position()));
	}

	/** What a draft would skip or where it should be written instead. It changes nothing. */
	@PostMapping("/discussions/{number}/previews")
	MessagePreviewView preview(@AuthenticationPrincipal SessionUser sender, @PathVariable ConversationId id, @PathVariable int number,
			@RequestBody DraftToPreview body)
	{
		return conversations.preview(sender.userId(), id, number, new MessageDraft(body.paragraphs()));
	}

	private static GeoPosition geoPosition(Position position)
	{
		return position == null ? null : new GeoPosition(position.latitude(), position.longitude());
	}
}
