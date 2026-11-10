package com.aemotionalline.infrastructure.web;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * The OpenAPI spec (CLAUDE.md, REST point 7), on only in development (springdoc.api-docs.enabled, from
 * AEMOTIONAL_API_DOCS): it describes the JSON as the API writes it (ids as UUID strings, a DiscussionId as its number),
 * which fields are required and which may be null (frontend point 2), and every error as a Problem with the full list of
 * codes, so the frontend can generate its types from it. frontend/openapi.json is a copy of it, kept current by a test.
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
		config.replaceWithSchema(LocalTime.class, new StringSchema().format("time").example("18:30"));
		config.replaceWithSchema(Duration.class, new StringSchema().format("duration").example("PT1H"));
		config.addRequestWrapperToIgnore(CsrfToken.class);
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
			problem.setRequired(List.of("title", "status", "detail", "instance", Problems.CODE));

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

	/**
	 * Which fields are required and which may be null (CLAUDE.md, frontend point 2), read from the records the API takes
	 * and returns: a field is required and never null unless it is an Optional or annotated @Nullable. In an answer such a
	 * field is still always present, null when empty; in a request it may be left out. A record used both in requests and
	 * in answers would need both readings at once, so it stops the spec instead.
	 */
	@Bean
	OpenApiCustomizer requiredAndNullable(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlers)
	{
		return api ->
		{
			Map<String, Class<?>> records = recordsOf(handlers);
			@SuppressWarnings("rawtypes")
			Map<String, Schema> schemas = api.getComponents().getSchemas();
			Set<String> requests = new HashSet<>();
			Set<String> answers = new HashSet<>();

			api.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
			{
				if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null)
				{
					operation.getRequestBody().getContent().values().forEach(media -> reach(media.getSchema(), schemas, requests));
				}

				if (operation.getResponses() != null)
				{
					operation.getResponses().values().stream().filter(response -> response.getContent() != null)
							.forEach(response -> response.getContent().values().forEach(media -> reach(media.getSchema(), schemas, answers)));
				}
			}));

			schemas.forEach((name, schema) ->
			{
				if (name.equals(PROBLEM) || schema.getProperties() == null)
				{
					return;
				}

				boolean answer = answers.contains(name);

				if (answer && requests.contains(name))
				{
					throw new IllegalStateException(name + " is used both in requests and in answers: a field can't be both optional and always present.");
				}

				Set<String> nullable = nullableComponents(records.get(name));
				@SuppressWarnings("unchecked")
				Map<String, Schema<?>> properties = schema.getProperties();
				List<String> required = new ArrayList<>();

				properties.replaceAll((property, value) -> nullable.contains(property) ? orNull(value) : value);
				properties.keySet().stream().filter(property -> answer || !nullable.contains(property)).forEach(required::add);
				schema.setRequired(required.isEmpty() ? null : required);
			});
		};
	}

	// The records springdoc describes, by the name it gives their schema: those the handlers return or take as a body, and
	// those inside them. Two records with the same simple name would share a schema, so they stop the spec.
	private static Map<String, Class<?>> recordsOf(RequestMappingHandlerMapping handlers)
	{
		Map<String, Class<?>> records = new HashMap<>();

		for (HandlerMethod handler : handlers.getHandlerMethods().values())
		{
			collect(ResolvableType.forMethodReturnType(handler.getMethod()), records);

			for (MethodParameter parameter : handler.getMethodParameters())
			{
				if (parameter.hasParameterAnnotation(RequestBody.class))
				{
					collect(ResolvableType.forMethodParameter(parameter), records);
				}
			}
		}

		return records;
	}

	private static void collect(ResolvableType type, Map<String, Class<?>> records)
	{
		Class<?> raw = type.resolve();

		if (raw == null)
		{
			return;
		}

		Arrays.stream(type.getGenerics()).forEach(generic -> collect(generic, records));

		if (!raw.isRecord() || records.get(raw.getSimpleName()) == raw)
		{
			return;
		}

		Class<?> other = records.putIfAbsent(raw.getSimpleName(), raw);

		if (other != null)
		{
			throw new IllegalStateException("Two records are named " + raw.getSimpleName() + ": " + other.getName() + ", " + raw.getName());
		}

		for (RecordComponent component : raw.getRecordComponents())
		{
			collect(ResolvableType.forType(component.getGenericType(), type), records);
		}
	}

	private static Set<String> nullableComponents(Class<?> record)
	{
		if (record == null)
		{
			return Set.of();
		}

		return Arrays.stream(record.getRecordComponents())
				.filter(component -> component.getType() == Optional.class || component.getAnnotatedType().isAnnotationPresent(Nullable.class))
				.map(RecordComponent::getName).collect(Collectors.toSet());
	}

	// One of the property's schema or null. The schema itself is left untouched: springdoc shares one object among the
	// properties of the same type (every id, for instance).
	private static Schema<?> orNull(Schema<?> property)
	{
		Schema<?> nothing = new Schema<>().specVersion(SpecVersion.V31).typesItem("null");

		return new Schema<>().specVersion(SpecVersion.V31).oneOf(List.of(property, nothing));
	}

	// The names of the schemas a schema leads to, through references, properties, items and alternatives.
	@SuppressWarnings("rawtypes")
	private static void reach(Schema<?> schema, Map<String, Schema> schemas, Set<String> into)
	{
		if (schema == null)
		{
			return;
		}

		if (schema.get$ref() != null)
		{
			String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);

			if (into.add(name))
			{
				reach(schemas.get(name), schemas, into);
			}

			return;
		}

		if (schema.getProperties() != null)
		{
			schema.getProperties().values().forEach(property -> reach(property, schemas, into));
		}

		reach(schema.getItems(), schemas, into);
		Stream.of(schema.getOneOf(), schema.getAnyOf(), schema.getAllOf()).filter(Objects::nonNull).flatMap(List::stream)
				.forEach(alternative -> reach(alternative, schemas, into));

		if (schema.getAdditionalProperties() instanceof Schema<?> values)
		{
			reach(values, schemas, into);
		}
	}
}
