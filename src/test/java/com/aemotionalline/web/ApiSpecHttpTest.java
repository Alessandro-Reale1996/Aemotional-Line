package com.aemotionalline.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.aemotionalline.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;

/**
 * The OpenAPI spec, as development sees it (CLAUDE.md, REST point 7): every endpoint of the controllers is in it, ids
 * are UUID strings, a DiscussionId an integer, and every error a Problem with the full list of codes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "AEMOTIONAL_API_DOCS=true")
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class ApiSpecHttpTest
{
	@LocalServerPort
	private int port;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	@Test
	void shouldDescribeEveryEndpointTheIdsAndTheErrors()
	{
		ApiClient anonymous = new ApiClient(port);
		ApiClient.Response answer = anonymous.get("/v3/api-docs");
		assertEquals(200, answer.status(), answer.body());
		JsonNode spec = answer.json();

		Set<String> documented = new HashSet<>(spec.path("paths").propertyNames());
		List<String> endpoints = mappings.getHandlerMethods().keySet().stream().flatMap(info -> info.getPatternValues().stream())
				.filter(path -> path.startsWith("/api/")).distinct().toList();
		assertFalse(endpoints.isEmpty());
		assertEquals(List.of(), endpoints.stream().filter(path -> !documented.contains(path)).toList());

		JsonNode schemas = spec.path("components").path("schemas");
		assertFalse(schemas.has("UserId"), "ids are described as strings, not as objects");
		assertUuid(schemas.path("AccountView").path("properties").path("id"));
		assertUuid(schemas.path("EchoView").path("properties").path("user"));
		assertEquals("integer", schemas.path("EchoView").path("properties").path("discussion").path("type").asString());
		assertEquals("string", schemas.path("NewMessage").path("properties").path("localTime").path("type").asString(), schemas.path("NewMessage").toString());
		assertUuid(schemas.path("ParagraphDraft").path("properties").path("question"));
		assertEquals("string", schemas.path("ConstraintSpec").path("properties").path("delay").path("type").asString(), schemas.path("ConstraintSpec").toString());
		assertEquals("string", schemas.path("ConstraintView").path("properties").path("delay").path("type").asString(), schemas.path("ConstraintView").toString());
		assertEquals("string", schemas.path("ClosingRequestView").path("properties").path("status").path("type").asString(), schemas.path("ClosingRequestView").toString());
		assertUuid(schemas.path("ArchiveEntry").path("properties").path("paragraphId"));
		assertFalse(schemas.has("ProposalContent"), "a proposal is described by its view");
		JsonNode from = spec.path("paths").path("/api/conversations/{id}/archive/messages").path("get").path("parameters").get(1);
		assertEquals("from", from.path("name").asString(), from.toString());
		assertEquals("date-time", from.path("schema").path("format").asString(), from.toString());

		Set<String> codes = new HashSet<>();
		schemas.path("Problem").path("properties").path("code").path("enum").forEach(code -> codes.add(code.asString()));
		assertTrue(codes.containsAll(Set.of("NOT_YOUR_TURN", "RULE_VIOLATED", "NOT_FOUND", "MALFORMED_REQUEST", "INCORRECT_PASSWORD",
				"AUTHENTICATION_REQUIRED", "INVALID_CREDENTIALS", "ACCESS_DENIED", "INTERNAL_ERROR")), codes.toString());
		assertEquals("#/components/schemas/Problem", spec.path("paths").path("/api/auth/login").path("post").path("responses").path("409")
				.path("content").path("application/problem+json").path("schema").path("$ref").asString());

		// Only the API, with what each endpoint really answers.
		assertFalse(spec.path("paths").has("/error"), "internal routes are not part of the contract");
		assertFalse(schemas.has("CsrfToken"), "not a request parameter");
		assertEquals(Set.of("201"), successes(spec, "/api/invitations", "post"));
		assertEquals(Set.of("201"), successes(spec, "/api/conversations/{id}/discussions/{number}/messages", "post"));
		assertEquals(Set.of("204"), successes(spec, "/api/negotiations/{id}/abandon", "post"));
		assertEquals(Set.of("204"), successes(spec, "/api/me/account-deletion", "post"));
		assertEquals(Set.of("202"), successes(spec, "/api/auth/password-reset", "post"));
		assertEquals(Set.of("204"), successes(spec, "/api/auth/csrf", "get"));
		assertEquals(Set.of("200"), successes(spec, "/api/conversations/{id}", "get"));
		assertEquals("#/components/schemas/AccountView", spec.path("paths").path("/api/auth/login").path("post").path("responses").path("200")
				.path("content").path("*/*").path("schema").path("$ref").asString(), spec.path("paths").path("/api/auth/login").toString());

		// Fixed sets of values are listed, and times and durations say their format.
		assertEquals(List.of("TIME", "REPLY_DELAY", "LOCATION"), strings(schemas.path("ConstraintSpec").path("properties").path("type").path("enum")));
		assertEquals(List.of("TIME", "REPLY_DELAY", "LOCATION"), strings(schemas.path("ConstraintView").path("properties").path("type").path("enum")));
		assertEquals(List.of("AGREEMENT", "DISCUSSION_TITLE"), strings(schemas.path("ProposalView").path("properties").path("kind").path("enum")));
		assertEquals("time", schemas.path("NewMessage").path("properties").path("localTime").path("format").asString());
		assertEquals("duration", schemas.path("ConstraintSpec").path("properties").path("delay").path("format").asString());

		assertEquals(200, anonymous.get("/swagger-ui/index.html").status());
	}

	private static Set<String> successes(JsonNode spec, String path, String method)
	{
		return spec.path("paths").path(path).path(method).path("responses").propertyNames().stream().filter(status -> status.startsWith("2")).collect(Collectors.toSet());
	}

	private static List<String> strings(JsonNode array)
	{
		List<String> values = new ArrayList<>();
		array.forEach(value -> values.add(value.asString()));

		return values;
	}

	private static void assertUuid(JsonNode property)
	{
		assertEquals("string", property.path("type").asString(), property.toString());
		assertEquals("uuid", property.path("format").asString(), property.toString());
	}
}
