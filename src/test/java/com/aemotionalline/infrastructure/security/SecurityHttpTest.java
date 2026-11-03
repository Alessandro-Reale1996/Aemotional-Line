package com.aemotionalline.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.aemotionalline.CapturingMailer;
import com.aemotionalline.CapturingMailer.Kind;
import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.application.UserService;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;
import com.aemotionalline.web.ApiClient;
import com.aemotionalline.web.TestEndpoints;

/**
 * The HTTP security of the API, over real HTTP (CLAUDE.md, S4): login and its session cookie, CSRF, the same answer for
 * every failed login, the lockout, session fixation, logout, sessions ended on password reset and on disabling, the path
 * rules, the 30-minute expiry, activation and password reset.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class SecurityHttpTest
{
	private static final String PASSWORD = "un cavallo sul tetto blu";

	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private UserService userService;

	@Autowired
	private PasswordHasher hasher;

	@Autowired
	private CapturingMailer mailer;

	@Autowired
	private JdbcClient jdbc;

	// LOGIN

	@Test
	void shouldLogInAndKeepTheUserInASessionNamedByIdNotByEmail()
	{
		User user = account(Role.THERAPIST);
		ApiClient client = new ApiClient(port);

		ApiClient.Response login = client.logIn(user.getEmail().value(), PASSWORD);

		assertEquals(200, login.status());
		assertTrue(login.body().contains("\"id\":\"" + user.getId().value() + "\""), login.body());
		assertTrue(login.body().contains("\"role\":\"THERAPIST\""), login.body());
		assertNotNull(client.cookie(ApiClient.SESSION));
		assertEquals(200, client.get("/api/auth/me").status());

		// The sessions table names the account by id; it expires after 30 minutes without requests.
		List<Integer> sessions = jdbc.sql("SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?")
				.param(user.getId().value().toString()).query(Integer.class).list();
		assertEquals(List.of(1800), sessions);
		assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME LIKE '%@%'").query(Long.class).single());
	}

	@Test
	void shouldCreateNoSessionForAnonymousRequests()
	{
		ApiClient client = new ApiClient(port);

		client.get("/api/auth/csrf");
		assertEquals(401, client.get("/api/conversations").status());
		client.post("/api/auth/login", "{\"email\":\"nobody@example.org\",\"password\":\"wrong password\"}");

		assertNull(client.cookie(ApiClient.SESSION));
	}

	@Test
	void shouldRefuseAPostWithoutTheCsrfToken()
	{
		User user = account(Role.PATIENT);
		ApiClient client = new ApiClient(port);
		client.get("/api/auth/csrf");

		ApiClient.Response forged = client.postWithoutCsrfHeader("/api/auth/login",
				"{\"email\":\"" + user.getEmail().value() + "\",\"password\":\"" + PASSWORD + "\"}");

		assertEquals(403, forged.status());
		assertEquals("ACCESS_DENIED", forged.code());
		assertNull(client.cookie(ApiClient.SESSION));
	}

	@Test
	void shouldGiveTheSameAnswerToEveryFailedLogin()
	{
		User active = account(Role.PATIENT);
		User disabled = account(Role.THERAPIST);
		disabled.disable();
		users.save(disabled);
		User pending = User.pending(UserId.random(), freshEmail(), "Pending", Role.PATIENT);
		users.save(pending);

		for (String[] credentials : List.of(new String[] { active.getEmail().value(), "not the password" },
				new String[] { freshEmail().value(), PASSWORD }, new String[] { disabled.getEmail().value(), PASSWORD },
				new String[] { pending.getEmail().value(), PASSWORD }))
		{
			ApiClient.Response refused = new ApiClient(port).logIn(credentials[0], credentials[1]);

			assertEquals(401, refused.status());
			assertEquals("INVALID_CREDENTIALS", refused.code());
			assertEquals("Invalid credentials.", refused.json().path("detail").asString());
		}
	}

	@Test
	void shouldLockTheAccountAfterFiveWrongPasswords()
	{
		User user = account(Role.PATIENT);
		ApiClient client = new ApiClient(port);

		for (int attempt = 1; attempt <= User.MAX_FAILED_LOGINS; attempt++)
		{
			assertEquals(401, client.logIn(user.getEmail().value(), "wrong password " + attempt).status());
		}

		ApiClient.Response locked = client.logIn(user.getEmail().value(), PASSWORD);
		assertEquals(401, locked.status());
		assertEquals("INVALID_CREDENTIALS", locked.code());
	}

	@Test
	void shouldGiveANewSessionIdAndANewCsrfTokenAtLogin()
	{
		User first = account(Role.PATIENT);
		User second = account(Role.PATIENT);
		ApiClient client = new ApiClient(port);

		client.logIn(first.getEmail().value(), PASSWORD);
		String session = client.cookie(ApiClient.SESSION);
		String token = client.cookie(ApiClient.CSRF);
		client.logIn(second.getEmail().value(), PASSWORD);

		assertNotEquals(session, client.cookie(ApiClient.SESSION));
		assertNotEquals(token, client.cookie(ApiClient.CSRF));
		assertTrue(client.get("/api/auth/me").body().contains(second.getId().value().toString()));

		// The old session id no longer opens anything.
		ApiClient stale = new ApiClient(port);
		stale.setCookie(ApiClient.SESSION, session);
		assertEquals(401, stale.get("/api/auth/me").status());
	}

	// COOKIES AND HEADERS (S5)

	@Test
	void shouldSetCookiesOnlyForHttpsTheWholeHostAndThisSite()
	{
		User user = account(Role.PATIENT);
		ApiClient client = new ApiClient(port);

		ApiClient.Response login = client.logIn(user.getEmail().value(), PASSWORD);

		String session = login.setCookie(ApiClient.SESSION).orElseThrow();
		assertTrue(session.contains("; Secure"), session);
		assertTrue(session.contains("; HttpOnly"), session);
		assertTrue(session.contains("; SameSite=Strict"), session);
		assertTrue(session.contains("; Path=/"), session);
		assertFalse(session.contains("Domain="), session);

		// The app must read the CSRF cookie: not HttpOnly, otherwise as strict.
		String csrf = login.setCookie(ApiClient.CSRF).filter(cookie -> !cookie.contains("Max-Age=0")).orElseThrow();
		assertTrue(csrf.contains("; Secure"), csrf);
		assertFalse(csrf.contains("HttpOnly"), csrf);
		assertTrue(csrf.contains("; SameSite=Strict"), csrf);
		assertTrue(csrf.contains("; Path=/"), csrf);
		assertFalse(csrf.contains("Domain="), csrf);
	}

	@Test
	void shouldSendStrictTransportSecurityOverHttpsOnly()
	{
		ApiClient client = new ApiClient(port);

		// Through the proxy (loopback is trusted), the request was HTTPS.
		ApiClient.Response https = client.get("/api/auth/csrf", "X-Forwarded-Proto", "https");
		assertEquals(List.of("max-age=31536000 ; includeSubDomains"), https.headers().allValues("Strict-Transport-Security"));

		ApiClient.Response http = client.get("/api/auth/csrf");
		assertEquals(List.of(), http.headers().allValues("Strict-Transport-Security"));
	}

	@Test
	void shouldNeverSendTheAddressAsReferrer()
	{
		ApiClient client = new ApiClient(port);

		for (ApiClient.Response response : List.of(client.get("/api/auth/csrf"), client.get("/api/auth/me"), client.get("/index.html")))
		{
			assertEquals(List.of("no-referrer"), response.headers().allValues("Referrer-Policy"));
			assertEquals(List.of("nosniff"), response.headers().allValues("X-Content-Type-Options"));
		}
	}

	// LOGOUT AND ENDED SESSIONS

	@Test
	void shouldLogOutOnlyTheCurrentSession()
	{
		User user = account(Role.PATIENT);
		ApiClient phone = new ApiClient(port);
		ApiClient computer = new ApiClient(port);
		phone.logIn(user.getEmail().value(), PASSWORD);
		computer.logIn(user.getEmail().value(), PASSWORD);

		assertEquals(204, phone.post("/api/auth/logout", "").status());

		assertNull(phone.cookie(ApiClient.SESSION));
		assertEquals(401, phone.get("/api/auth/me").status());
		assertEquals(200, computer.get("/api/auth/me").status());
	}

	@Test
	void shouldEndEverySessionOfAnAccountWhenItsPasswordIsReset()
	{
		User user = account(Role.PATIENT);
		ApiClient phone = new ApiClient(port);
		ApiClient computer = new ApiClient(port);
		phone.logIn(user.getEmail().value(), PASSWORD);
		computer.logIn(user.getEmail().value(), PASSWORD);

		ApiClient forgetful = new ApiClient(port);
		forgetful.get("/api/auth/csrf");
		assertEquals(202, forgetful.post("/api/auth/password-reset", "{\"email\":\"" + user.getEmail().value() + "\"}").status());
		String code = mailer.lastCode(user.getEmail(), Kind.PASSWORD_RESET).orElseThrow();
		assertEquals(204, forgetful.post("/api/auth/password-reset/confirm", "{\"code\":\"" + code + "\",\"password\":\"una password tutta nuova\"}").status());

		assertEquals(401, phone.get("/api/auth/me").status());
		assertEquals(401, computer.get("/api/auth/me").status());
		assertEquals(200, new ApiClient(port).logIn(user.getEmail().value(), "una password tutta nuova").status());
	}

	@Test
	void shouldEndTheSessionsOfADisabledTherapist()
	{
		User therapist = account(Role.THERAPIST);
		User administrator = account(Role.ADMINISTRATOR);
		ApiClient client = new ApiClient(port);
		client.logIn(therapist.getEmail().value(), PASSWORD);

		userService.disableTherapist(administrator.getId(), therapist.getId());

		assertEquals(401, client.get("/api/auth/me").status());
	}

	@Test
	void shouldExpireASessionAfterThirtyMinutesWithoutRequests()
	{
		User user = account(Role.PATIENT);
		ApiClient client = new ApiClient(port);
		client.logIn(user.getEmail().value(), PASSWORD);

		jdbc.sql("UPDATE SPRING_SESSION SET LAST_ACCESS_TIME = LAST_ACCESS_TIME - ?, EXPIRY_TIME = EXPIRY_TIME - ? WHERE PRINCIPAL_NAME = ?")
				.params(31 * 60 * 1000L, 31 * 60 * 1000L, user.getId().value().toString()).update();

		assertEquals(401, client.get("/api/auth/me").status());
	}

	// PATHS

	@Test
	void shouldProtectThePaths()
	{
		ApiClient anonymous = new ApiClient(port);
		ApiClient patient = new ApiClient(port);
		ApiClient administrator = new ApiClient(port);
		patient.logIn(account(Role.PATIENT).getEmail().value(), PASSWORD);
		administrator.logIn(account(Role.ADMINISTRATOR).getEmail().value(), PASSWORD);

		ApiClient.Response refused = anonymous.get("/api/admin/probe");
		assertEquals(401, refused.status());
		assertEquals("AUTHENTICATION_REQUIRED", refused.code());
		assertEquals(401, anonymous.get("/api/auth/me").status());
		assertEquals(403, patient.get("/api/admin/probe").status());
		assertEquals(200, administrator.get("/api/admin/probe").status());
		// Outside /api nothing is served yet, logged in or not.
		assertEquals(403, administrator.get("/index.html").status());
	}

	// ACTIVATION AND PASSWORD RESET

	@Test
	void shouldActivateAnAccountThroughItsLink()
	{
		User administrator = account(Role.ADMINISTRATOR);
		EmailAddress email = freshEmail();
		userService.createTherapist(administrator.getId(), email, "Dott.ssa Rossi");
		String code = mailer.lastCode(email, Kind.ACTIVATION).orElseThrow();
		ApiClient client = new ApiClient(port);
		client.get("/api/auth/csrf");

		ApiClient.Response weak = client.post("/api/auth/activate", "{\"code\":\"" + code + "\",\"password\":\"short\"}");
		assertEquals(400, weak.status());
		assertEquals("PASSWORD_TOO_SHORT", weak.code());
		assertEquals(400, client.post("/api/auth/activate", "{\"code\":\"not-a-code\",\"password\":\"" + PASSWORD + "\"}").status());
		assertEquals(204, client.post("/api/auth/activate", "{\"code\":\"" + code + "\",\"password\":\"" + PASSWORD + "\"}").status());

		assertEquals(200, client.logIn(email.value(), PASSWORD).status());
	}

	@Test
	void shouldAnswerEveryPasswordResetRequestTheSameWay()
	{
		User user = account(Role.PATIENT);
		ApiClient client = new ApiClient(port);
		client.get("/api/auth/csrf");

		for (String email : List.of(user.getEmail().value(), freshEmail().value(), "not an email"))
		{
			ApiClient.Response answer = client.post("/api/auth/password-reset", "{\"email\":\"" + email + "\"}");

			assertEquals(202, answer.status());
			assertEquals("", answer.body());
		}

		assertFalse(mailer.lastCode(user.getEmail(), Kind.PASSWORD_RESET).isEmpty());
	}

	// HELPERS

	private User account(Role role)
	{
		User user = User.pending(UserId.random(), freshEmail(), role.name().toLowerCase(), role);
		user.activate(hasher.hash(PASSWORD));
		users.save(user);

		return user;
	}

	private static EmailAddress freshEmail()
	{
		return new EmailAddress(UUID.randomUUID() + "@example.org");
	}
}
