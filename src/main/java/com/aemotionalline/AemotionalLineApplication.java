package com.aemotionalline;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.aemotionalline.infrastructure.security.FirstAdministratorCommand;

@SpringBootApplication
public class AemotionalLineApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(AemotionalLineApplication.class);

		// The first-administrator command runs without the web server: no port is opened, and it works while the
		// application is already running on the same machine.
		if (Arrays.stream(args).anyMatch(argument -> argument.startsWith("--" + FirstAdministratorCommand.OPTION))) {
			application.setWebApplicationType(WebApplicationType.NONE);
		}

		application.run(args);
	}

}
