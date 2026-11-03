package com.aemotionalline.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

import tools.jackson.databind.JsonNode;

/**
 * The API's answers over real HTTP (CLAUDE.md, REST points 2-4, R2): every failure as a Problem with its status and code,
 * malformed requests named by field without repeating the values, and the JSON shape of ids, instants and empty values.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class ApiErrorsHttpTest
{
	private static final String PASSWORD = "un cavallo sul tetto blu";

	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordHasher hasher;

	@Test
	void shouldAnswerEveryKindOfFailureAsAProblemWithItsCode()
	{
		ApiClient client = loggedIn(Role.PATIENT);

		assertProblem(client.get("/api/test/errors/rule"), 409, "NOT_YOUR_TURN");
		assertProblem(client.get("/api/test/errors/guard"), 409, "RULE_VIOLATED");
		assertProblem(client.get("/api/test/errors/domain-not-found"), 404, "NOT_FOUND");
		assertProblem(client.get("/api/test/errors/not-found"), 404, "NOT_FOUND");
		assertProblem(client.get("/api/test/errors/invalid"), 400, "PASSWORD_TOO_SHORT");
		assertProblem(client.get("/api/test/errors/password"), 400, "INCORRECT_PASSWORD");
		assertProblem(client.get("/api/nothing-here"), 404, "NOT_FOUND");
		assertProblem(client.send("DELETE", "/api/test/echo", "{}"), 405, "MALFORMED_REQUEST");
		assertProblem(client.get("/api/admin/probe"), 403, "ACCESS_DENIED");
		assertProblem(new ApiClient(port).get("/api/test/errors/rule"), 401, "AUTHENTICATION_REQUIRED");

		// The detail is the rule's English message, for developers.
		assertEquals("It is not this user's turn", client.get("/api/test/errors/rule").json().path("detail").asString());
	}

	@Test
	void shouldGiveAnErrorIdForAnUnexpectedFailureAndNothingOfItsMessage()
	{
		ApiClient.Response failure = loggedIn(Role.PATIENT).get("/api/test/errors/boom");

		assertProblem(failure, 500, "INTERNAL_ERROR");
		assertEquals("An unexpected error occurred.", failure.json().path("detail").asString());
		assertEquals(36, failure.json().path("errorId").asString().length());
		// The exception's message ("Unexpected failure about boom") is nowhere in the answer.
		assertFalse(failure.body().contains("Unexpected failure"), failure.body());
	}

	@Test
	void shouldNameTheFieldOfAMalformedRequestWithoutRepeatingItsValue()
	{
		ApiClient client = loggedIn(Role.PATIENT);

		ApiClient.Response unknown = client.post("/api/test/echo", "{\"text\":\"ok\",\"surname\":\"secret-value\"}");
		assertProblem(unknown, 400, "MALFORMED_REQUEST");
		assertEquals("Unknown field 'surname'.", unknown.json().path("detail").asString());

		ApiClient.Response badId = client.post("/api/test/echo", "{\"user\":\"secret-value\",\"text\":\"ok\"}");
		assertProblem(badId, 400, "MALFORMED_REQUEST");
		assertEquals("Missing or malformed value of field 'user'.", badId.json().path("detail").asString());
		assertFalse(badId.body().contains("secret-value"), badId.body());

		ApiClient.Response badInstant = client.post("/api/test/echo", "{\"at\":\"secret-value\",\"text\":\"ok\"}");
		assertEquals("Missing or malformed value of field 'at'.", badInstant.json().path("detail").asString());

		ApiClient.Response missing = client.post("/api/test/echo", "{\"user\":null}");
		assertProblem(missing, 400, "MALFORMED_REQUEST");
		assertEquals("The field 'text' is required.", missing.json().path("detail").asString());

		ApiClient.Response notJson = client.post("/api/test/echo", "{\"text\": secret-value");
		assertProblem(notJson, 400, "MALFORMED_REQUEST");
		assertFalse(notJson.body().contains("secret-value"), notJson.body());

		ApiClient.Response badPath = client.get("/api/test/conversations/secret-value");
		assertProblem(badPath, 400, "MALFORMED_REQUEST");
		assertEquals("Malformed value of 'id'.", badPath.json().path("detail").asString());
	}

	@Test
	void shouldWriteIdsInstantsAndEmptyValuesAsDecided()
	{
		ApiClient client = loggedIn(Role.PATIENT);
		UUID user = UUID.randomUUID();

		JsonNode echo = client.post("/api/test/echo", "{\"user\":\"" + user + "\",\"at\":\"2026-11-03T10:15:30.123456Z\",\"text\":\"ok\"}").json();

		assertEquals(user.toString(), echo.path("user").asString());
		UUID.fromString(echo.path("conversation").asString());
		assertTrue(echo.path("discussion").isNumber());
		assertEquals("2", echo.path("discussion").asString());
		assertEquals("2026-11-03T10:15:30.123456Z", echo.path("at").asString());
		assertTrue(echo.has("never") && echo.get("never").isNull());
		assertTrue(echo.path("nothing").isArray() && echo.path("nothing").isEmpty());
		assertEquals("PATIENT", echo.path("role").asString());

		// An id in the path is its plain UUID too.
		assertEquals("\"" + user + "\"", client.get("/api/test/conversations/" + user).body());
	}

	@Test
	void shouldAnswerARequestRefusedBeforeSpringMvcAsAProblem()
	{
		// The security firewall refuses a path parameter: the container's error page answers.
		assertProblem(new ApiClient(port).get("/api/auth/csrf;x=1"), 400, "MALFORMED_REQUEST");
	}

	@Test
	void shouldNotServeTheApiSpecOutsideDevelopment()
	{
		assertEquals(401, new ApiClient(port).get("/v3/api-docs").status());
		assertEquals(403, loggedIn(Role.ADMINISTRATOR).get("/v3/api-docs").status());
	}

	private static void assertProblem(ApiClient.Response response, int status, String code)
	{
		assertEquals(status, response.status(), response.body());
		assertEquals(code, response.code(), response.body());
		assertEquals(status, response.json().path("status").asInt());
		assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"), response.headers().toString());
	}

	private ApiClient loggedIn(Role role)
	{
		User user = User.pending(UserId.random(), new EmailAddress(UUID.randomUUID() + "@example.org"), "Someone", role);
		user.activate(hasher.hash(PASSWORD));
		users.save(user);
		ApiClient client = new ApiClient(port);
		assertEquals(200, client.logIn(user.getEmail().value(), PASSWORD).status());

		return client;
	}
}
