# Aemotional-Line

A deliberately slow, structured messaging system for couples, supervised by their couples therapist: the partners
negotiate an agreement, write in paragraphs that answer each other, open specific discussions, accept constraints on
when and where they read and write, and keep a consultable archive of everything.

## State

Done: the domain model, persistence on MariaDB, the application services and the security layer (accounts, couple
invitations, login with sessions in the database). Not built yet: the REST endpoints beyond login and account
management, and the React frontend. `CLAUDE.md` records the detailed state and every design decision.

## Build and test

Requirements: Java 25; Docker running (the tests start MariaDB in a container).

```bash
./mvnw test
```

Spring Boot 4, Spring Data JDBC, Flyway, Spring Security with Spring Session JDBC, JUnit 5 and Testcontainers.

## Running

See [docs/deployment.md](docs/deployment.md): configuration, database, reverse proxy and HTTPS, the first
administrator, data retention, and what to check with a privacy advisor before going live.
