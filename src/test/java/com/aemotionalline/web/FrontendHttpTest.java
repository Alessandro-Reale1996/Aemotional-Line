package com.aemotionalline.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.UserRepository;
import com.aemotionalline.infrastructure.security.SecurityConfiguration;

/**
 * The React app served from the jar (CLAUDE.md, frontend points 1 and 8, F2), with stand-ins for its files in the test
 * resources: its pages open at their own addresses for anyone, its files are served as they are, a missing file and an
 * unknown API address are not the app, only GET is taken outside /api, only the files named by their content may be
 * kept by the browser (F11), and every answer carries the CSP and the Permissions-Policy.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public class FrontendHttpTest
{
	private static final String APP = "test stand-in for the app";

	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private PasswordHasher hasher;

	@Test
	void shouldOpenTheAppAtTheAddressOfAnyOfItsPages()
	{
		ApiClient anonymous = new ApiClient(port);

		for (String page : List.of("/", "/index.html", "/accedi", "/attiva?codice=abc", "/conversazioni/0193c9e4-1d2b-7c3a-9f00-123456789abc/discorsi/2",
				"/an/address/the/app/doesnt/know"))
		{
			ApiClient.Response answer = anonymous.get(page);

			assertEquals(200, answer.status(), page);
			assertTrue(answer.body().contains(APP), page);
			assertEquals(Optional.of("text/html"), answer.headers().firstValue("Content-Type").map(type -> type.split(";")[0]), page);
		}

		ApiClient.Response file = anonymous.get("/assets/app.js");
		assertEquals(200, file.status());
		assertTrue(file.body().contains("standIn"));
	}

	@Test
	void shouldNotAnswerWithTheAppWhatIsNotOneOfItsPages()
	{
		ApiClient anonymous = new ApiClient(port);

		ApiClient.Response missing = anonymous.get("/assets/missing.js");
		assertEquals(404, missing.status());
		assertFalse(missing.body().contains(APP));

		// Under /api an unknown address stays an API answer, never the app: "log in" before, "not found" after.
		ApiClient anna = new HttpCouple(port, users, couples, hasher).anna;

		for (String api : List.of("/api", "/api/unknown", "/api/conversations"))
		{
			ApiClient.Response refused = anonymous.get(api);
			assertEquals(401, refused.status(), api);
			assertEquals("AUTHENTICATION_REQUIRED", refused.code(), api);

			ApiClient.Response unknown = anna.get(api);
			assertEquals(404, unknown.status(), api);
			assertEquals("NOT_FOUND", unknown.code(), api);
		}
	}

	@Test
	void shouldTakeOnlyGetOutsideTheApi()
	{
		ApiClient anonymous = new ApiClient(port);
		anonymous.get("/api/auth/csrf");

		for (String method : List.of("POST", "PUT", "DELETE", "PATCH"))
		{
			ApiClient.Response answer = anonymous.send(method, "/accedi", "{}");

			assertEquals(401, answer.status(), method);
			assertFalse(answer.body().contains(APP), method);
		}
	}

	@Test
	void shouldLetTheBrowserKeepOnlyTheFilesNamedByTheirContent()
	{
		ApiClient anonymous = new ApiClient(port);
		ApiClient anna = new HttpCouple(port, users, couples, hasher).anna;

		ApiClient.Response asset = anonymous.get("/assets/app.js");
		assertEquals(200, asset.status());
		assertEquals(Optional.of("max-age=31536000, public, immutable"), asset.headers().firstValue("Cache-Control"));
		assertTrue(asset.headers().firstValue("Pragma").isEmpty(), "no Pragma contradicting it");

		// The pages, a missing file and every API answer, therapy content above all: never kept by the browser.
		List<ApiClient.Response> neverKept = List.of(anonymous.get("/"), anonymous.get("/index.html"), anonymous.get("/accedi"),
				anonymous.get("/favicon.svg"), anonymous.get("/assets/missing.js"), anonymous.get("/api/limits"), anonymous.get("/api/auth/me"),
				anna.get("/api/auth/me"), anna.get("/api/me/couples"), anna.get("/api/unknown"));

		for (ApiClient.Response answer : neverKept)
		{
			assertTrue(answer.headers().firstValue("Cache-Control").orElse("").contains("no-store"), answer.headers().map().toString());
		}
	}

	@Test
	void shouldSendTheSecurityPoliciesWithEveryAnswer()
	{
		ApiClient anonymous = new ApiClient(port);

		for (String address : List.of("/", "/accedi", "/assets/app.js", "/api/limits", "/api/auth/me"))
		{
			ApiClient.Response answer = anonymous.get(address);

			assertEquals(Optional.of(SecurityConfiguration.CONTENT_SECURITY_POLICY), answer.headers().firstValue("Content-Security-Policy"), address);
			assertEquals(Optional.of(SecurityConfiguration.PERMISSIONS_POLICY), answer.headers().firstValue("Permissions-Policy"), address);
		}
	}
}
