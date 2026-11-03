package com.aemotionalline.infrastructure.web;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aemotionalline.application.RequestErrorCode;
import com.aemotionalline.domain.common.ErrorCode;
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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * The OpenAPI spec (CLAUDE.md, REST point 7), on only in development (springdoc.api-docs.enabled, from
 * AEMOTIONAL_API_DOCS): it describes the JSON as the API writes it (ids as UUID strings, a DiscussionId as its number)
 * and every error as a Problem with the full list of codes, so the frontend can generate its types from it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class ApiDocsConfiguration
{
	private static final String PROBLEM = "Problem";
	private static final Map<String, String> ERRORS = Map.of("400", "Malformed request (MALFORMED_REQUEST, or a link or password code)",
			"401", "Login required, or failed (AUTHENTICATION_REQUIRED, INVALID_CREDENTIALS)", "403", "Forbidden (ACCESS_DENIED)", "404",
			"Not found (NOT_FOUND)", "409", "A rule refused the request (its code)", "500", "Unexpected error (INTERNAL_ERROR, with an errorId)");

	ApiDocsConfiguration()
	{
		SpringDocUtils config = SpringDocUtils.getConfig();

		for (Class<?> id : List.of(UserId.class, CoupleId.class, CoupleInvitationId.class, ConversationId.class, NegotiationId.class, ProposalId.class,
				MessageId.class, ParagraphId.class, ConstraintChangeRequestId.class))
		{
			config.replaceWithSchema(id, new StringSchema().format("uuid"));
		}

		config.replaceWithSchema(DiscussionId.class, new IntegerSchema().description("The discussion's number in its conversation (0 is the main one)."));
	}

	@Bean
	OpenApiCustomizer problems()
	{
		return api ->
		{
			List<String> codes = Stream.of(ErrorCode.values(), RequestErrorCode.values(), HttpErrorCode.values()).flatMap(Arrays::stream).map(Enum::name)
					.distinct().sorted().toList();
			Schema<?> problem = new ObjectSchema()
					.addProperty("type", new StringSchema())
					.addProperty("title", new StringSchema())
					.addProperty("status", new IntegerSchema())
					.addProperty("detail", new StringSchema().description("In English, for developers."))
					.addProperty("instance", new StringSchema())
					.addProperty(Problems.CODE, new StringSchema()._enum(codes).description("Stable: the frontend picks its message by it."))
					.addProperty(Problems.ERROR_ID, new StringSchema().description("Only with INTERNAL_ERROR: the id under which the error is logged."));

			if (api.getComponents() == null)
			{
				api.setComponents(new Components());
			}

			api.getComponents().addSchemas(PROBLEM, problem);

			if (api.getPaths() != null)
			{
				api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> ERRORS.forEach((status, description) ->
				{
					if (operation.getResponses() != null && !operation.getResponses().containsKey(status))
					{
						operation.getResponses().addApiResponse(status, new ApiResponse().description(description).content(new Content()
								.addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
										new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM)))));
					}
				})));
			}
		};
	}
}
