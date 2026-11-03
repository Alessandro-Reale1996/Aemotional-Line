package com.aemotionalline.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * No endpoint can be left open by mistake (CLAUDE.md, REST point 6): every route Spring MVC knows under /api answers 401
 * without a login, except this explicit list of public ones, and every /api/admin route refuses a non-administrator. A
 * new endpoint is checked as soon as it exists.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class RouteGuardHttpTest
{
	/** The only routes open without a login (the logout is a security filter, not a route). */
	private static final Set<String> PUBLIC = Set.of("GET /api/auth/csrf", "POST /api/auth/login", "POST /api/auth/activate",
			"POST /api/auth/password-reset", "POST /api/auth/password-reset/confirm", "GET /api/limits");

	private static final String PASSWORD = "un cavallo sul tetto blu";

	@LocalServerPort
	private int port;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordHasher hasher;

	@Test
	void shouldRequireALoginOnEveryRouteButThePublicOnes()
	{
		List<String> routes = routes();
		assertTrue(routes.containsAll(PUBLIC), "a public route no longer exists: " + PUBLIC);

		ApiClient anonymous = new ApiClient(port);
		anonymous.get("/api/auth/csrf");

		assertEquals(List.of(), routes.stream().filter(route -> !PUBLIC.contains(route)).filter(route -> call(anonymous, route).status() != 401).toList());
	}

	@Test
	void shouldKeepTheAdministratorRoutesForAdministrators()
	{
		ApiClient patient = loggedIn(Role.PATIENT);
		List<String> administration = routes().stream().filter(route -> route.contains(" /api/admin/")).toList();
		assertFalse(administration.isEmpty());

		assertEquals(List.of(), administration.stream().filter(route -> call(patient, route).status() != 403).toList());
	}

	// "METHOD /path" for every route under /api (a route without methods answers any: GET stands for them).
	private List<String> routes()
	{
		return mappings.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPatternValues().stream().filter(path -> path.startsWith("/api/"))
						.flatMap(path -> (info.getMethodsCondition().getMethods().isEmpty() ? Set.of(RequestMethod.GET) : info.getMethodsCondition().getMethods())
								.stream().map(method -> method.name() + " " + path)))
				.distinct().sorted().toList();
	}

	private static ApiClient.Response call(ApiClient client, String route)
	{
		String[] parts = route.split(" ");
		String path = parts[1].replaceAll("\\{[^}]+}", UUID.randomUUID().toString());

		return client.send(parts[0], path, parts[0].equals("GET") ? null : "{}");
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
