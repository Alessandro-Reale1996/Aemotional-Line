package com.aemotionalline;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real MariaDB in a Docker container for the tests that need a database: the same engine as production, so collations,
 * the UUID type and row locks behave as they will there. Spring Boot connects the datasource to it (@ServiceConnection),
 * and Flyway applies the migrations to the empty database at startup.
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
}
