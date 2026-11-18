# Aemotional-Line

A deliberately slow, structured messaging system for couples, supervised by their couples therapist: the partners
negotiate an agreement, write in paragraphs that answer each other, open specific discussions, accept constraints on
when and where they read and write, and keep a consultable archive of everything.

## State

Done: the domain model, persistence on MariaDB, the application services, the security layer (accounts, couple
invitations, login with sessions in the database), the REST API and the React frontend (`frontend/`), which the jar
serves: accounts and administration, couples and invitations, the stipule, the conversation read as letters and written
in paragraphs, closings, constraints and the archive, on a computer or a phone. `CLAUDE.md` records the detailed state
and every design decision.

## Build and test

Requirements: Java 25; Docker running (the tests start MariaDB in a container).

```bash
./mvnw test
```

`./mvnw package` also builds the frontend into the jar, after its own checks; it needs no Node installed (it downloads
its own into `frontend/node`). Working on the application day to day: [dev/README.md](dev/README.md).

Spring Boot 4, Spring Data JDBC, Flyway, Spring Security with Spring Session JDBC, springdoc-openapi, JUnit 5 and
Testcontainers; React 19 with React Router, React-Bootstrap and Vite, TypeScript, Vitest and Playwright.

## Running

See [docs/deployment.md](docs/deployment.md): configuration, database, reverse proxy and HTTPS, the first
administrator, data retention, and what to check with a privacy advisor before going live.

## Building the frontend

See [docs/frontend-notes.md](docs/frontend-notes.md): the API's conventions, what the generated types can't say, and
how the app is served (security policies, caching, phones).
