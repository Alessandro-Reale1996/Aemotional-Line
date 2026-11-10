import { startEnvironment } from './environment';

/** Starts the end-to-end environment once for the whole run; the function it returns stops it. */
export default async function globalSetup()
{
  const environment = await startEnvironment();
  // The tests read the administrator from here: it lives only as long as this run.
  process.env.E2E_ADMIN_EMAIL = environment.administrator.email;
  process.env.E2E_ADMIN_PASSWORD = environment.administrator.password;

  return () => environment.stop();
}
