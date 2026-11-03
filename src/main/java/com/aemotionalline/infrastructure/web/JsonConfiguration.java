package com.aemotionalline.infrastructure.web;

import java.util.UUID;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.module.SimpleModule;

/**
 * How the domain's types look in JSON (CLAUDE.md, REST point 2), without any Jackson annotation in the domain: every id
 * is its plain UUID string (read back from one), a DiscussionId is its number (the conversation is in the URL). Instants
 * are ISO-8601 UTC, enums their names, an empty Optional null: Jackson 3's defaults.
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfiguration
{
	@Bean
	JacksonModule identifiers()
	{
		SimpleModule module = new SimpleModule("aemotional-identifiers");
		uuid(module, UserId.class, UserId::value, UserId::new);
		uuid(module, CoupleId.class, CoupleId::value, CoupleId::new);
		uuid(module, CoupleInvitationId.class, CoupleInvitationId::value, CoupleInvitationId::new);
		uuid(module, ConversationId.class, ConversationId::value, ConversationId::new);
		uuid(module, NegotiationId.class, NegotiationId::value, NegotiationId::new);
		uuid(module, ProposalId.class, ProposalId::value, ProposalId::new);
		uuid(module, MessageId.class, MessageId::value, MessageId::new);
		uuid(module, ParagraphId.class, ParagraphId::value, ParagraphId::new);
		uuid(module, ConstraintChangeRequestId.class, ConstraintChangeRequestId::value, ConstraintChangeRequestId::new);
		module.addSerializer(DiscussionId.class, new ValueSerializer<>()
		{
			@Override
			public void serialize(DiscussionId id, JsonGenerator generator, SerializationContext context)
			{
				generator.writeNumber(id.discussionNumber());
			}
		});

		return module;
	}

	private static <T> void uuid(SimpleModule module, Class<T> type, Function<T, UUID> value, Function<UUID, T> create)
	{
		module.addSerializer(type, new ValueSerializer<T>()
		{
			@Override
			public void serialize(T id, JsonGenerator generator, SerializationContext context)
			{
				generator.writeString(value.apply(id).toString());
			}
		});
		module.addDeserializer(type, new ValueDeserializer<T>()
		{
			@Override
			public T deserialize(JsonParser parser, DeserializationContext context)
			{
				String text = parser.getValueAsString();

				try
				{
					return create.apply(UUID.fromString(text));
				}
				catch (RuntimeException malformed)
				{
					throw InvalidFormatException.from(parser, "Not a UUID.", text, type);
				}
			}
		});
	}
}
