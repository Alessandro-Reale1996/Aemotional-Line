import { type ChildProcess, execFileSync, spawn } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import { existsSync, mkdirSync, openSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * The environment of the end-to-end tests (CLAUDE.md, frontend point 9 and F3), apart from the development one: a MariaDB
 * kept in memory and a Mailpit of their own, started with plain docker (no compose needed), the jar the build made, and a
 * first administrator. The passwords are generated for each run and never written anywhere.
 */
export const BACKEND_PORT = 8181;
export const BASE_URL = `http://localhost:${BACKEND_PORT}`;
const DATABASE_PORT = 3307;
const SMTP_PORT = 1026;
const MAILPIT_PORT = 8026;
/** Mailpit's web interface and API: the tests read the emails the backend sends there. */
export const MAILPIT_URL = `http://localhost:${MAILPIT_PORT}`;
const ROOT = resolve(import.meta.dirname, '..', '..');
const JAR = resolve(ROOT, 'target', 'aemotional-line-0.0.1-SNAPSHOT.jar');
const RESULTS = resolve(import.meta.dirname, '..', 'test-results');

export interface Environment
{
  administrator: { email: string; password: string };
  stop(): Promise<void>;
}

export async function startEnvironment(): Promise<Environment>
{
  if (!existsSync(JAR))
  {
    throw new Error(`${JAR} is missing: build it first (npm run e2e does it).`);
  }

  const run = randomUUID().slice(0, 8);
  const database = `aemotional-e2e-db-${run}`;
  const mail = `aemotional-e2e-mail-${run}`;
  const databasePassword = secret();
  let server: ChildProcess | undefined;

  const stop = async () =>
  {
    if (server && server.exitCode === null)
    {
      const exited = new Promise((done) => server?.once('exit', done));
      server.kill('SIGTERM');
      await exited;
    }

    for (const container of [database, mail])
    {
      execFileSync('docker', ['rm', '--force', container], { stdio: 'ignore' });
    }
  };

  try
  {
    // The password reaches docker through the environment, never through the command line.
    execFileSync('docker', ['run', '--detach', '--rm', '--name', database, '--publish', `127.0.0.1:${DATABASE_PORT}:3306`,
      '--tmpfs', '/var/lib/mysql', '--env', 'MARIADB_DATABASE=aemotional_line', '--env', 'MARIADB_USER=aemotional',
      '--env', 'MARIADB_PASSWORD', '--env', 'MARIADB_RANDOM_ROOT_PASSWORD=1',
      '--health-cmd', 'healthcheck.sh --connect --innodb_initialized', '--health-interval', '2s',
      'mariadb:11.4', '--character-set-server=utf8mb4', '--collation-server=utf8mb4_uca1400_ai_ci'],
    { stdio: 'ignore', env: { ...process.env, MARIADB_PASSWORD: databasePassword } });
    execFileSync('docker', ['run', '--detach', '--rm', '--name', mail, '--publish', `127.0.0.1:${SMTP_PORT}:1025`,
      '--publish', `127.0.0.1:${MAILPIT_PORT}:8025`, 'axllent/mailpit:v1.33.1'], { stdio: 'ignore' });
    await until(() => execFileSync('docker', ['inspect', '--format', '{{.State.Health.Status}}', database]).toString().trim() === 'healthy',
      120, 'MariaDB to be ready');

    const backend = {
      ...process.env,
      AEMOTIONAL_DB_URL: `jdbc:mariadb://localhost:${DATABASE_PORT}/aemotional_line`,
      AEMOTIONAL_DB_USER: 'aemotional',
      AEMOTIONAL_DB_PASSWORD: databasePassword,
      AEMOTIONAL_MAIL_HOST: 'localhost',
      AEMOTIONAL_MAIL_PORT: String(SMTP_PORT),
      AEMOTIONAL_BASE_URL: BASE_URL,
    };
    // Mailpit speaks plain SMTP, like the development one (dev/application.properties).
    const plainSmtp = ['--spring.mail.properties.mail.smtp.starttls.enable=false', '--spring.mail.properties.mail.smtp.starttls.required=false'];

    const administrator = { email: `amministrazione-${run}@example.test`, password: secret() };
    const printed = execFileSync('java', ['-jar', JAR, `--create-admin=${administrator.email}`, '--name=Amministrazione', ...plainSmtp],
      { env: backend, stdio: ['ignore', 'pipe', 'ignore'] }).toString();
    const code = /codice=([A-Za-z0-9_-]+)/.exec(printed)?.[1];

    if (!code)
    {
      throw new Error('The first-administrator command printed no activation link.');
    }

    mkdirSync(RESULTS, { recursive: true });
    const log = openSync(resolve(RESULTS, 'backend.log'), 'w');
    server = spawn('java', ['-jar', JAR, `--server.port=${BACKEND_PORT}`, '--server.address=127.0.0.1', ...plainSmtp],
      { env: backend, stdio: ['ignore', log, log] });
    await until(async () =>
    {
      if (server?.exitCode !== null)
      {
        throw new Error(`The backend stopped while starting: see ${resolve(RESULTS, 'backend.log')}`);
      }

      return (await fetch(`${BASE_URL}/api/limits`).catch(() => undefined))?.ok === true;
    }, 120, 'the backend to start');

    await activate(code, administrator.password);

    return { administrator, stop };
  }
  catch (failure)
  {
    await stop();
    throw failure;
  }
}

// The activation, as the page /attiva does it: the CSRF cookie first, then its value in the header.
async function activate(code: string, password: string)
{
  const csrf = await fetch(`${BASE_URL}/api/auth/csrf`);
  const token = /__Host-XSRF-TOKEN=([^;]+)/.exec(csrf.headers.getSetCookie().join('\n'))?.[1];

  if (!token)
  {
    throw new Error('The backend set no CSRF cookie.');
  }

  const answer = await fetch(`${BASE_URL}/api/auth/activate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Cookie: `__Host-XSRF-TOKEN=${token}`, 'X-XSRF-TOKEN': token },
    body: JSON.stringify({ code, password }),
  });

  if (answer.status !== 204)
  {
    throw new Error(`The administrator's activation answered ${answer.status}.`);
  }
}

function secret()
{
  return randomBytes(24).toString('base64url');
}

async function until(condition: () => boolean | Promise<boolean>, seconds: number, what: string)
{
  const deadline = Date.now() + seconds * 1000;

  while (Date.now() < deadline)
  {
    if (await condition())
    {
      return;
    }

    await new Promise((wait) => setTimeout(wait, 500));
  }

  throw new Error(`Gave up waiting for ${what} after ${seconds} seconds.`);
}
