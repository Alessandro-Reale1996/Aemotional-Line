package com.aemotionalline;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.aemotionalline.infrastructure.security.SpringSessionAccountSessions;

/**
 * The outside world, for the tests that need Spring: a real MariaDB in a Docker container (the same engine as production,
 * so collations, the UUID type and row locks behave as they will there; Spring Boot connects the datasource to it and
 * Flyway migrates it), a mailer that captures the emails, and a recorder of ended sessions (which still ends them). Every Spring test imports this
 * one configuration, so they share one context and one container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration
{
	@Bean
	@ServiceConnection
	MariaDBContainer mariaDb()
	{
		return new MariaDBContainer(DockerImageName.parse("mariadb:11.4"));
	}

	@Bean
	@Primary
	CapturingMailer capturingMailer()
	{
		return new CapturingMailer();
	}

	@Bean
	@Primary
	RecordingSessions recordingSessions(SpringSessionAccountSessions sessions)
	{
		return new RecordingSessions(sessions);
	}
}
