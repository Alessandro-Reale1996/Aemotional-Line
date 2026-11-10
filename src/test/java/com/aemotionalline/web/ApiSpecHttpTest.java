package com.aemotionalline.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The OpenAPI spec, as development sees it (CLAUDE.md, REST point 7): every endpoint of the controllers is in it, ids
 * are UUID strings, a DiscussionId an integer, every error a Problem with the full list of codes, required and nullable
 * fields marked (frontend point 2), and frontend/openapi.json, from which the frontend generates its types, is current.
 * No test endpoints here, so the spec is the real one.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "AEMOTIONAL_API_DOCS=true")
@Import(TestcontainersConfiguration.class)
public class ApiSpecHttpTest
{
	/** The copy of the spec the frontend is built on, and where a newer one is left when it differs. */
	static final Path COMMITTED = Path.of("frontend", "openapi.json");
	static final Path FRESH = Path.of("target", "openapi.json");

	private static final JsonMapper JSON = JsonMapper.builder().build();

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
		assertUuid(schemas.path("MessageView").path("properties").path("sender"));
		assertEquals("integer", schemas.path("DiscussionView").path("properties").path("id").path("type").asString());
		assertEquals("string", nonNull(schemas.path("NewMessage").path("properties").path("localTime")).path("type").asString(), schemas.path("NewMessage").toString());
		assertUuid(nonNull(schemas.path("ParagraphDraft").path("properties").path("question")));
		assertEquals("string", nonNull(schemas.path("ConstraintSpec").path("properties").path("delay")).path("type").asString(), schemas.path("ConstraintSpec").toString());
		assertEquals("string", nonNull(schemas.path("ConstraintView").path("properties").path("delay")).path("type").asString(), schemas.path("ConstraintView").toString());
		assertEquals("string", schemas.path("ClosingRequestView").path("properties").path("status").path("type").asString(), schemas.path("ClosingRequestView").toString());
		assertUuid(nonNull(schemas.path("ArchiveEntry").path("properties").path("paragraphId")));
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
		assertEquals("time", nonNull(schemas.path("NewMessage").path("properties").path("localTime")).path("format").asString());
		assertEquals("duration", nonNull(schemas.path("ConstraintSpec").path("properties").path("delay")).path("format").asString());

		assertEquals(200, anonymous.get("/swagger-ui/index.html").status());
	}

	@Test
	void shouldSayWhichFieldsAreRequiredAndWhichMayBeNull()
	{
		JsonNode schemas = spec().path("components").path("schemas");

		// In an answer every field is present: one that may be empty is null, whether a plain value, an Optional or a view.
		JsonNode proposal = schemas.path("ProposalView");
		assertEquals(Set.copyOf(proposal.path("properties").propertyNames()), Set.copyOf(strings(proposal.path("required"))), proposal.toString());
		assertTrue(isNullable(proposal.path("properties").path("text")), proposal.toString());
		assertFalse(isNullable(proposal.path("properties").path("id")), proposal.toString());
		assertTrue(isNullable(schemas.path("MessageView").path("properties").path("readAt")));
		JsonNode draft = schemas.path("NegotiationView").path("properties").path("draft");
		assertTrue(isNullable(draft), draft.toString());
		assertEquals("#/components/schemas/ProposalView", nonNull(draft).path("$ref").asString(), draft.toString());
		assertFalse(isNullable(schemas.path("ConversationView").path("properties").path("discussions")), "a list is never null");

		// In a request a field that may be empty can be left out.
		JsonNode paragraph = schemas.path("ParagraphDraft");
		assertEquals(Set.of("type", "body", "references", "selfCitations"), Set.copyOf(strings(paragraph.path("required"))), paragraph.toString());
		assertTrue(isNullable(paragraph.path("properties").path("subtitle")));
		assertEquals(List.of("paragraphs"), strings(schemas.path("NewMessage").path("required")));

		assertTrue(strings(schemas.path("Problem").path("required")).containsAll(List.of("status", "code")));
	}

	@Test
	void shouldMatchTheSpecTheFrontendIsBuiltOn() throws IOException
	{
		ObjectNode spec = (ObjectNode) spec();
		// The server's address depends on the port of the moment; the frontend doesn't need it.
		spec.remove("servers");

		if (Files.exists(COMMITTED) && JSON.readTree(Files.readString(COMMITTED)).equals(spec))
		{
			return;
		}

		Files.writeString(FRESH, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(spec)) + "\n");
		fail("The API's spec differs from " + COMMITTED + ". Check the new version, then copy it: cp " + FRESH + " " + COMMITTED);
	}

	private JsonNode spec()
	{
		ApiClient.Response answer = new ApiClient(port).get("/v3/api-docs");
		assertEquals(200, answer.status(), answer.body());

		return answer.json();
	}

	// The same JSON with the members of every object in alphabetical order, so the file's differences stay readable.
	private static JsonNode sorted(JsonNode node)
	{
		if (node.isObject())
		{
			ObjectNode sorted = JsonNodeFactory.instance.objectNode();
			node.propertyNames().stream().sorted().forEach(name -> sorted.set(name, sorted(node.get(name))));

			return sorted;
		}

		if (node.isArray())
		{
			var array = JsonNodeFactory.instance.arrayNode();
			node.forEach(element -> array.add(sorted(element)));

			return array;
		}

		return node;
	}

	private static boolean isNullable(JsonNode property)
	{
		boolean nullable = false;

		for (JsonNode alternative : property.path("oneOf"))
		{
			nullable |= alternative.path("type").asString().equals("null");
		}

		return nullable;
	}

	// A nullable property is "one of" its schema or null: its schema.
	private static JsonNode nonNull(JsonNode property)
	{
		for (JsonNode alternative : property.path("oneOf"))
		{
			if (!alternative.path("type").asString().equals("null"))
			{
				return alternative;
			}
		}

		return property;
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
