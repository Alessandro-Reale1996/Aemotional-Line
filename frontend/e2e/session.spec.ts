import { expect, test } from './fixtures';
import { administrator, createTherapist, emailedLink, freshEmail, freshPassword, logIn, logOut } from './helpers';

/**
 * Client and session (CLAUDE.md, F4), on the jar: logging in and out through the interface, the pages opened from the
 * emails, the session that expires or is ended by inactivity, the tabs that follow each other, a phone-sized screen.
 */
const ADMINISTRATOR = 'Amministrazione';

test('logs in and out through the interface', async ({ page }) =>
{
  await page.goto('/');
  await expect(page).toHaveURL(/\/accedi$/);
  await expect(page.getByText('Se usi un dispositivo condiviso, non salvare la password e ricordati di uscire.')).toBeVisible();

  await logIn(page, administrator().email, administrator().password);
  // The administrator's home is the administration (F5).
  await expect(page.getByRole('heading', { name: 'Terapeuti' })).toBeVisible();
  await expect(page).toHaveURL(/\/amministrazione$/);

  await logOut(page, ADMINISTRATOR);
  await page.goto('/');
  await expect(page).toHaveURL(/\/accedi$/);
});

test('says the same thing for every failed login', async ({ page }) =>
{
  // An address without an account: the administrator's own would be locked after five tries.
  await logIn(page, freshEmail('nessuno'), 'una password qualsiasi');

  await expect(page.getByRole('alert')).toHaveText(
    'Email o password non validi. Dopo 5 tentativi sbagliati di seguito l’accesso resta bloccato per 15 minuti.');
});

test('activates an account from the emailed link, then chooses a new password', async ({ page, browser }) =>
{
  const email = freshEmail('terapeuta');
  const name = 'Dott.ssa Bianchi';
  const first = freshPassword();
  const second = freshPassword();
  await createTherapist(browser, email, name);

  await page.goto(await emailedLink(email, '/attiva'));
  await expect(page).toHaveURL(/\/attiva$/);
  await expect(page.getByText(/^Da 12 a 64 caratteri/)).toBeVisible();
  await page.getByLabel('Password', { exact: true }).fill(first);
  await page.getByRole('button', { name: 'Attiva l’account' }).click();
  await expect(page.getByText('L’account è attivo. Ora puoi accedere.')).toBeVisible();
  await page.getByRole('link', { name: 'Accedi' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(first);
  await page.getByRole('button', { name: 'Accedi' }).click();
  await expect(page.getByRole('heading', { name: 'Coppie' })).toBeVisible();
  await logOut(page, name);

  await page.getByRole('link', { name: 'Password dimenticata?' }).click();
  await expect(page.getByRole('heading', { name: 'Password dimenticata' })).toBeVisible();
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: 'Invia il link' }).click();
  await expect(page.getByText(/^Se l’indirizzo corrisponde a un account attivo/)).toBeVisible();

  await page.goto(await emailedLink(email, '/reimposta-password'));
  await expect(page).toHaveURL(/\/reimposta-password$/);
  await page.getByLabel('Password', { exact: true }).fill(second);
  await page.getByRole('button', { name: 'Salva la nuova password' }).click();
  await expect(page.getByText(/^La password è cambiata/)).toBeVisible();

  await logIn(page, email, second);
  await expect(page.getByRole('heading', { name: 'Coppie' })).toBeVisible();
});

test('goes to the login when the session has expired, and comes back to the page', async ({ page, context }) =>
{
  await page.clock.install();
  await logIn(page, administrator().email, administrator().password);
  await expect(page.getByRole('heading', { name: 'Terapeuti' })).toBeVisible();
  await page.goto('/una-pagina');
  await expect(page.getByRole('heading', { name: 'Pagina non trovata' })).toBeVisible();

  // The server forgets the session; the request that keeps it alive while typing finds out.
  await context.clearCookies({ name: '__Host-SESSION' });
  await page.clock.fastForward('10:30');
  await page.keyboard.press('Shift');

  await expect(page.getByText('La sessione è scaduta. Accedi di nuovo per continuare.')).toBeVisible();
  await expect(page).toHaveURL(/\/accedi$/);
  await page.getByLabel('Email').fill(administrator().email);
  await page.getByLabel('Password', { exact: true }).fill(administrator().password);
  await page.getByRole('button', { name: 'Accedi' }).click();
  await expect(page).toHaveURL(/\/una-pagina$/);
  await expect(page.getByRole('heading', { name: 'Pagina non trovata' })).toBeVisible();
});

test('ends the session after 30 minutes of inactivity', async ({ page }) =>
{
  await page.clock.install();
  await logIn(page, administrator().email, administrator().password);
  await expect(page.getByRole('heading', { name: 'Terapeuti' })).toBeVisible();

  await page.clock.fastForward('29:00');
  await expect(page.getByRole('heading', { name: 'Terapeuti' })).toBeVisible();
  await page.clock.fastForward('01:30');

  await expect(page.getByText('La sessione è stata chiusa dopo 30 minuti di inattività, per proteggere la tua riservatezza.')).toBeVisible();
  expect(await page.evaluate(async () => (await fetch('/api/auth/me')).status), 'the session is over on the server too').toBe(401);
});

test('a login or a logout in one tab reaches the others', async ({ page, context }) =>
{
  await logIn(page, administrator().email, administrator().password);
  await expect(page.getByRole('heading', { name: 'Terapeuti' })).toBeVisible();
  const other = await context.newPage();
  await other.goto('/');
  await expect(other.getByRole('heading', { name: 'Terapeuti' })).toBeVisible();

  await logOut(page, ADMINISTRATOR);
  await expect(other.getByText('La sessione è stata chiusa in un’altra scheda.')).toBeVisible();

  await other.getByLabel('Email').fill(administrator().email);
  await other.getByLabel('Password', { exact: true }).fill(administrator().password);
  await other.getByRole('button', { name: 'Accedi' }).click();
  await expect(other.getByRole('heading', { name: 'Terapeuti' })).toBeVisible();
  await expect(page.getByText(`Hai già effettuato l’accesso con l’account di «${ADMINISTRATOR}».`)).toBeVisible();
  await other.close();
});

test('works on a phone-sized screen', async ({ page }) =>
{
  await page.setViewportSize({ width: 390, height: 844 });
  await logIn(page, administrator().email, administrator().password);
  await expect(page.getByRole('heading', { name: 'Terapeuti' })).toBeVisible();

  await expect(page.getByRole('button', { name: ADMINISTRATOR, exact: true })).toBeHidden();
  await page.getByRole('button', { name: 'Menu' }).click();
  await logOut(page, ADMINISTRATOR);
});
