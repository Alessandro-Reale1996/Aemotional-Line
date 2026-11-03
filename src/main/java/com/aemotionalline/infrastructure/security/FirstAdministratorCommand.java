package com.aemotionalline.infrastructure.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.aemotionalline.application.UserService;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.infrastructure.mail.AccountLinks;

/**
 * Creates the first administrator from the server's terminal (CLAUDE.md, security point 6a, S4 decision 3):
 * {@code java -jar aemotional-line.jar --create-admin=<email> [--name=<display name>]}. Refused once an administrator
 * exists. The activation link is printed on the terminal only, never logged nor emailed; then the application exits. Run
 * this way, the application doesn't start its web server (see AemotionalLineApplication).
 */
@Component
public class FirstAdministratorCommand implements ApplicationRunner
{
	public static final String OPTION = "create-admin";
	private static final String NAME_OPTION = "name";
	private static final String DEFAULT_NAME = "Amministratore";

	private final UserService users;
	private final ConfigurableApplicationContext context;
	private final String baseUrl;

	FirstAdministratorCommand(UserService users, ConfigurableApplicationContext context, @Value("${aemotional.base-url}") String baseUrl)
	{
		this.users = users;
		this.context = context;
		this.baseUrl = baseUrl;
	}

	@Override
	public void run(ApplicationArguments arguments)
	{
		if (!arguments.containsOption(OPTION))
		{
			return;
		}

		int status = 0;

		try
		{
			String link = createFrom(arguments);
			System.out.println("Open this link to activate the administrator account and choose its password (valid 7 days, once):");
			System.out.println(link);
		}
		catch (RuntimeException failure)
		{
			System.err.println("The administrator was not created: " + failure.getMessage());
			status = 1;
		}

		int exit = status;
		System.exit(SpringApplication.exit(context, () -> exit));
	}

	/** Creates (or, if still pending, renews) the first administrator and returns the activation link to print. */
	String createFrom(ApplicationArguments arguments)
	{
		List<String> email = arguments.getOptionValues(OPTION);

		if (email == null || email.size() != 1 || email.getFirst().isBlank())
		{
			throw new IllegalArgumentException("usage: --" + OPTION + "=<email> [--" + NAME_OPTION + "=<display name>]");
		}

		List<String> name = arguments.getOptionValues(NAME_OPTION);
		String displayName = name == null || name.isEmpty() || name.getFirst().isBlank() ? DEFAULT_NAME : name.getFirst();

		return AccountLinks.activation(baseUrl, users.createFirstAdministrator(new EmailAddress(email.getFirst()), displayName));
	}
}
