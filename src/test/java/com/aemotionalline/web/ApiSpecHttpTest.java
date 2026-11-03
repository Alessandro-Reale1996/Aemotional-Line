package com.aemotionalline.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

		Set<String> codes = new HashSet<>();
		schemas.path("Problem").path("properties").path("code").path("enum").forEach(code -> codes.add(code.asString()));
		assertTrue(codes.containsAll(Set.of("NOT_YOUR_TURN", "RULE_VIOLATED", "NOT_FOUND", "MALFORMED_REQUEST", "INCORRECT_PASSWORD",
				"AUTHENTICATION_REQUIRED", "INVALID_CREDENTIALS", "ACCESS_DENIED", "INTERNAL_ERROR")), codes.toString());
		assertEquals("#/components/schemas/Problem", spec.path("paths").path("/api/auth/login").path("post").path("responses").path("409")
				.path("content").path("application/problem+json").path("schema").path("$ref").asString());

		assertEquals(200, anonymous.get("/swagger-ui/index.html").status());
	}

	private static void assertUuid(JsonNode property)
	{
		assertEquals("string", property.path("type").asString(), property.toString());
		assertEquals("uuid", property.path("format").asString(), property.toString());
	}
}
