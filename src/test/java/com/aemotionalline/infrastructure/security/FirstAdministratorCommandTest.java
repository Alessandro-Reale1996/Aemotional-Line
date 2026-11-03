package com.aemotionalline.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.CapturingMailer;
import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.InvalidRequestException;
import com.aemotionalline.application.UserService;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.AccountStatus;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserRepository;

/**
 * The first-administrator command (CLAUDE.md, S4 decision 3). The property gives this class a Spring context, hence a
 * MariaDB container, of its own: the shared test database already has administrators.
 */
@SpringBootTest(properties = "aemotional.test.database=without-administrators")
@Import(TestcontainersConfiguration.class)
public class FirstAdministratorCommandTest
{
	private static final EmailAddress EMAIL = new EmailAddress("admin@example.org");
	private static final String PASSWORD = "un cavallo sul tetto blu";

	@Autowired
	private FirstAdministratorCommand command;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository users;

	@Autowired
	private CapturingMailer mailer;

	@Test
	void shouldCreateTheFirstAdministratorOnlyWhileThereIsNone()
	{
		assertThrows(IllegalArgumentException.class, () -> command.createFrom(arguments("--create-admin")));
		assertThrows(IllegalArgumentException.class, () -> command.createFrom(arguments("--create-admin= ")));

		String first = command.createFrom(arguments("--create-admin=Admin@Example.org", "--name=Operatore"));

		assertTrue(first.startsWith("http://localhost:8080/attiva?codice="), first);
		User administrator = users.findByEmail(EMAIL).orElseThrow();
		assertEquals(Role.ADMINISTRATOR, administrator.getRole());
		assertEquals(AccountStatus.PENDING, administrator.getStatus());
		assertEquals("Operatore", administrator.getDisplayName());

		// Still pending: the same email gets a new link and the old one stops working; any other email is refused.
		String second = command.createFrom(arguments("--create-admin=admin@example.org"));
		assertNotEquals(first, second);
		assertThrows(DomainException.class, () -> command.createFrom(arguments("--create-admin=other@example.org")));
		assertThrows(InvalidRequestException.class, () -> userService.activate(code(first), PASSWORD));
		userService.activate(code(second), PASSWORD);

		DomainException refused = assertThrows(DomainException.class, () -> command.createFrom(arguments("--create-admin=other@example.org")));
		assertEquals("An administrator already exists.", refused.getMessage());
		// The link is for the terminal only: no email was sent.
		assertTrue(mailer.to(EMAIL).isEmpty());
	}

	private static DefaultApplicationArguments arguments(String... arguments)
	{
		return new DefaultApplicationArguments(arguments);
	}

	private static String code(String link)
	{
		return link.substring(link.indexOf("codice=") + "codice=".length());
	}
}
