# Notes for the frontend

What the React app needs to know about the REST API, beyond the types generated from the OpenAPI spec. `CLAUDE.md` records
why each choice was made.

## The contract

`frontend/openapi.json` is the spec the app generates its types from. A backend test regenerates it from the code and
fails when the copy is out of date, leaving the new version in `target/openapi.json`; every answer of the backend's HTTP
tests is checked against it. With the backend started with `AEMOTIONAL_API_DOCS=true` the same spec is at `/v3/api-docs`,
Swagger UI at `/swagger-ui.html`; both are off by default and stay off in production.

The spec lists every `/api` route, what it answers on success, the error format and the full list of error codes. It also
says which fields are required: in an answer every field is present, and one that may be empty is declared as one of its
type or `null`; in a request a field that may be empty can be left out.

The logout isn't in the spec, because a security filter handles it: `POST /api/auth/logout` answers 204.

## Session and CSRF

- The app and the API share one host. In development, let Vite proxy `/api` to the backend on port 8080.
- Call `GET /api/auth/csrf` first: it sets the `__Host-XSRF-TOKEN` cookie. Send that cookie's value in the `X-XSRF-TOKEN`
  header with every POST, PUT and DELETE, reading it each time: the login replaces the token.
- `POST /api/auth/login` with `email` and `password` answers 200 with the account and sets `__Host-SESSION`, which JavaScript
  can't read. Any failure, whatever its reason, is the same 401 `INVALID_CREDENTIALS`.
- A session ends after 30 minutes without requests. From then on the API answers 401 `AUTHENTICATION_REQUIRED`: go back to
  the login page.
- Both cookies are `Secure` with the `__Host-` prefix, so a browser accepts them only in a secure context.
  `http://localhost` is one: Chromium and Firefox accept them there, through Vite's proxy too (checked on 2026-11-10; Safari
  not checked). Use `localhost`, not `127.0.0.1`, in development. How to run everything: `dev/README.md`.

## Pages linked from the emails

The emails link to `/attiva?codice=...` (activation) and `/reimposta-password?codice=...` (password reset). Read the code,
remove it from the address bar at once (`history.replaceState`), then send it with `POST /api/auth/activate` or
`POST /api/auth/password-reset/confirm`.

## JSON

- Ids are UUID strings, except a discussion, which is its number in the conversation (0 is the main one).
- Instants are ISO-8601 UTC: show them in local time. A time of day is `HH:mm` in a request; an answer may add the seconds,
  so parse it.
- Durations are ISO-8601 and come back normalized by Java: a delay sent as `P30D` is read back as `PT720H`. Parse them, don't
  compare their text.
- Enums travel by name: translate them in the app.
- In an answer every field is always present: `null` when empty. Lists are never `null`.
- In a request, an unknown field is refused with a 400, so a misspelled field shows up at once.
- The server never alters what a user wrote.

## Errors

Every error is an RFC 9457 Problem Details answer with a `code` member. Choose the Italian message by `code`; `detail` is in
English, for developers.

| Status | Meaning |
|---|---|
| 400 | `MALFORMED_REQUEST` is a bug in the app and `detail` names the field; `INVALID_LINK`, `INCORRECT_PASSWORD` and the `PASSWORD_*` codes are the user's |
| 401 / 403 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS` / `ACCESS_DENIED` (a missing CSRF header too) |
| 404 | `NOT_FOUND`, also for what the user may not see: an outsider can't tell that a conversation exists |
| 409 | A rule refused the action: one code per rule (`NOT_YOUR_TURN`, `TEXT_TOO_LONG`...). `RULE_VIOLATED` means the app sent something the interface shouldn't allow |
| 500 | `INTERNAL_ERROR`, with an `errorId` to show the user, so that they can report it |

A double click does no harm on the server, where nothing happens twice, but the second request can answer 409: disable the
button while a request is in flight.

## Limits

`GET /api/limits` needs no login and gives every text limit, for the counters: paragraphs, proposals, justifications, the
number of paragraphs in a message, the longest reply delay, the password rule. Text lengths are in UTF-16 units, like
JavaScript's `length`; the password is counted in code points.

## Where to start

`GET /api/me/couples` lists the user's couples, each with its conversation id if it has one. `GET /api/me/summary` lists what
is waiting for the user: invitations, negotiations on their turn, unopened messages, requests to answer. The therapist has
no unopened counts, since they read freely and nothing is recorded.

## The conversation

- A message of the other partner not opened yet is a **closed envelope** (`closedEnvelope: true`): sender and instant, nothing
  else. `POST .../messages/{messageId}/open` returns its content and records the first opening. It always needs a body: `{}`
  is enough.
- **There are no read receipts.** The sender's `readAt` is always `null`; show nothing that suggests the other has read.
- A partner can reply only after opening the last message, and never twice in a row: `lastSender` tells whose turn it is.
- A draft refers to sent paragraphs by id, and only to the message being answered. A question is answered by a `POINTED`
  paragraph, whose title is computed. Each type takes only its own fields: `subtitle` on a question, or `title` on a pointed
  paragraph, is a 400. A message has at most 20 paragraphs.
- Before sending, call `POST .../previews`. `questionsLeftUnanswered` is a warning: the user may send anyway.
  `blockingDiscussions` means the draft must be written in that discussion instead.
- Send `localTime` when the user acts. Send `position` **only when a location constraint applies** to that user and action,
  so as not to transmit personal data for nothing: the server uses it for the check and doesn't store it. A constraint that
  needs a missing value refuses the action (`CONSTRAINT_NOT_SATISFIED`).
- The reported time and position can't be verified by the server: don't describe them to users as proof.

## Negotiations

An agreement negotiation starts the conversation if the couple has none, otherwise it changes the agreement. A discussion
negotiation opens a specific discussion, optionally from a paragraph. Accepting the last proposal takes effect at once: the
answer gives the conversation and the discussion opened, and the negotiation no longer exists. An unsent draft is visible
only to its author. Every send needs a justification. Concluded negotiations are read from the archive.

## Closings and constraints

- Don't offer closing the main discussion (0): it can't be closed.
- Asking the therapist to close needs a failed attempt with the other partner first (`THERAPIST_REQUEST_NOT_ALLOWED_YET`).
- The justification of that request is empty for the other partner. The other's declared outcome stays empty until both have
  declared: don't suggest it is missing by mistake.
- A constraint proposal holds the whole new set; an empty list lifts them all. They apply at the second approval.

## Archive

Everything under `/archive` stays readable after the couple has ended.

- `POST /archive/search` takes the text in the body, so that the word stays out of every address. Don't copy it into the
  page's own URL or the router's state in the address bar. Unopened messages are left out entirely.
- `GET /archive/messages?from=&to=` takes UTC instants, `to` excluded: convert the user's local days. Each unopened message
  is one closed envelope, which hides how many paragraphs it has.
- `GET /archive/topics/{paragraphId}` shows unopened messages as closed envelopes in their place. A paragraph that doesn't
  exist and one the user hasn't opened get the same 404.

## Irreversible actions

Deleting one's account, ending a couple, deleting its data and disabling a therapist need the account's password in the
body. A wrong one is a 400 `INCORRECT_PASSWORD` and counts as a failed login: five in a row lock the account for 15 minutes.
The irreversible steps of the normal flow (closing a discussion, refusing, abandoning) ask for no password: confirm them
with a dialog.

## How the app is served

The compiled app is inside the jar, and Spring serves it on the API's own host: its files as they are, and `index.html`
for every other address outside `/api` whose last part has no extension, so that the app's pages open from an email's
link or on a reload, and the app shows its own "not found" page. A missing file is a 404. Outside `/api` only GET is
accepted.

Every answer carries this Content-Security-Policy:

```
default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
```

So: no inline `<script>` or `<style>`, no `style` attributes in the HTML (styles set from JavaScript are allowed), no
`eval`, nothing loaded from another host (fonts, scripts and images included), images only from the app or `data:`, and
no native form submission: handle every form's submit in JavaScript. Show users' text as plain text, never as HTML.
Vite's development server doesn't apply the policy, so check it on the compiled app. The `Permissions-Policy` allows only
the position (`geolocation=(self)`), which a location constraint needs.
