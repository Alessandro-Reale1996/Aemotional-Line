# Development

How to run the application on your machine while working on it. Nothing in this folder goes into the jar; the operator's
guide is `docs/deployment.md`.

## What you need

- Java 25 (the Maven wrapper does the rest).
- Docker, with the compose plugin for the services below (`docker compose version`; on Ubuntu:
  `sudo apt install docker-compose-v2`).
- Node 26 LTS, for the frontend (`node --version`).

## Once

Copy `dev/.env.example` to `dev/.env` (git ignores it) and choose the two passwords of the development database.

## Every day

1. The services: MariaDB on `127.0.0.1:3306`, and Mailpit, which keeps every email the application sends, at
   <http://localhost:8025>. Their data stay in a docker volume.

   ```bash
   docker compose -f dev/compose.yaml up -d
   ```

2. The backend, on <http://localhost:8080> (Swagger UI at `/swagger-ui.html`):

   ```bash
   dev/backend.sh
   ```

   From Eclipse: run `AemotionalLineApplication` with the program argument
   `--spring.config.additional-location=file:dev/application.properties` and the environment variable
   `AEMOTIONAL_DB_PASSWORD` set to the value in `dev/.env`.

3. The frontend, on <http://localhost:5173>, which passes `/api` to the backend:

   ```bash
   cd frontend && npm ci && npm run dev
   ```

   (`npm ci` only the first time and after a change of `package-lock.json`.)

The first administrator: `dev/backend.sh --create-admin=you@example.org --name=Operatore` prints an activation link that
opens the frontend (no web server starts). Everyone else gets their link by email: read it in Mailpit.

Stop the services with `docker compose -f dev/compose.yaml down`; add `-v` to erase the database as well.

## Checks

In `frontend/`:

- `npm run check`: the types generated from `openapi.json`, TypeScript, ESLint and the Vitest tests. The jar is not built
  if this fails.
- `npm run e2e`: builds the jar and runs the Playwright tests on it, in Chromium and Firefox, in an environment of their
  own (a MariaDB in memory, a Mailpit, the backend on port 8181), started with plain docker and removed at the end.
  `npx playwright test` reuses the last jar. The first time, `npx playwright install chromium firefox` downloads the
  browsers.

The backend's tests: `./mvnw test` (Docker must be running), never with `clean` while Eclipse is open.

When the API changes, `./mvnw test -Dtest=ApiSpecHttpTest` fails and leaves the new spec in `target/openapi.json`: check
it, copy it to `frontend/openapi.json`, and the next `npm run` regenerates the types.
