package com.aemotionalline.infrastructure.web;

import java.util.UUID;
import java.util.function.Function;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

/** Ids in URLs (path variables, query parameters) are their plain UUIDs, as in JSON; a malformed one is a 400. */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer
{
	@Override
	public void addFormatters(FormatterRegistry registry)
	{
		uuid(registry, UserId.class, UserId::new);
		uuid(registry, CoupleId.class, CoupleId::new);
		uuid(registry, CoupleInvitationId.class, CoupleInvitationId::new);
		uuid(registry, ConversationId.class, ConversationId::new);
		uuid(registry, NegotiationId.class, NegotiationId::new);
		uuid(registry, ProposalId.class, ProposalId::new);
		uuid(registry, MessageId.class, MessageId::new);
		uuid(registry, ParagraphId.class, ParagraphId::new);
		uuid(registry, ConstraintChangeRequestId.class, ConstraintChangeRequestId::new);
	}

	private static <T> void uuid(FormatterRegistry registry, Class<T> type, Function<UUID, T> create)
	{
		registry.addConverter(String.class, type, text -> create.apply(UUID.fromString(text)));
	}
}
