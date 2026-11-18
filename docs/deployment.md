# Deploying Aemotional-Line

This guide is for whoever installs and runs the application. It holds therapy conversations between couples and their
therapist: treat every part of the installation as handling health data.

The backend, REST API included, and the React frontend are complete: the jar serves both, the app and its API on one
host.

## 1. What you need

- **Java 25** to run the application (a single jar).
- **MariaDB 11.4 LTS** (or later). The schema needs MariaDB 10.10 or later (native `UUID` columns, the
  `utf8mb4_uca1400_ai_ci` collation); the tests run against 11.4.
- **An SMTP server** that accepts encrypted connections (STARTTLS on port 587, or implicit TLS on port 465). Activation,
  password-reset and invitation emails carry one-time links.
- **A reverse proxy** in front of the application (nginx, Caddy, HAProxy...). It terminates HTTPS, redirects HTTP to
  HTTPS and limits login attempts per IP address. The application itself only speaks plain HTTP and must not be
  reachable except through the proxy.
- **A host name of its own** for the application (for example `app.example.org`), served at the root path. The login
  cookies use the `__Host-` prefix, which browsers accept only over HTTPS, for a whole host, without a `Domain`.

## 2. Build

```bash
./mvnw -DskipTests package
```

The jar is `target/aemotional-line-0.0.1-SNAPSHOT.jar`, with the React app inside. The build compiles the frontend
too: the first time it downloads Node 26 into `frontend/node` and the npm packages (network access needed), and it
refuses to build the jar if the frontend's own checks (types, lint, unit tests) fail. To run the backend's tests as
well, Docker must be running: they start MariaDB in a container.

## 3. Configuration

Everything that differs between installations comes from environment variables; no secret is kept in the repository.

| Variable | Default | Meaning |
|---|---|---|
| `AEMOTIONAL_DB_URL` | `jdbc:mariadb://localhost:3306/aemotional_line` | JDBC address of the database |
| `AEMOTIONAL_DB_USER` | `aemotional` | database user |
| `AEMOTIONAL_DB_PASSWORD` | *(empty)* | database password |
| `AEMOTIONAL_BASE_URL` | `http://localhost:8080` | the public HTTPS address of the application (`https://app.example.org`); used in the links sent by email |
| `AEMOTIONAL_MAIL_HOST` | *(empty)* | SMTP server; when empty, sending an email fails with an error |
| `AEMOTIONAL_MAIL_PORT` | `587` | SMTP port |
| `AEMOTIONAL_MAIL_USER` / `AEMOTIONAL_MAIL_PASSWORD` | *(empty)* | SMTP credentials |
| `AEMOTIONAL_MAIL_FROM` | `no-reply@aemotional-line.local` | sender address of the emails |
| `AEMOTIONAL_RETENTION_AFTER_END` | *(empty: disabled)* | retention period after a couple ends, as an ISO-8601 duration (`P365D`); see section 8 |
| `AEMOTIONAL_API_DOCS` | `false` | serves the OpenAPI spec and Swagger UI. For development only: in production it would hand out the whole map of the API |

Other Spring Boot properties can be overridden in a `config/application.properties` file in the directory the
application is started from. Keep that file readable only by the account that runs the application.

**Mail with implicit TLS (port 465).** The default requires STARTTLS. For a server that uses TLS from the first byte,
put this in `config/application.properties`:

```properties
spring.mail.properties.mail.smtp.ssl.enable=true
spring.mail.properties.mail.smtp.starttls.enable=false
spring.mail.properties.mail.smtp.starttls.required=false
```

## 4. Database

Create an empty database and a user that owns it:

```sql
CREATE DATABASE aemotional_line CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
CREATE USER 'aemotional'@'%' IDENTIFIED BY '<a long random password>';
GRANT ALL PRIVILEGES ON aemotional_line.* TO 'aemotional'@'%';
```

Restrict the user's host (`'%'`) to the application's address. The application creates and updates its tables itself
(Flyway migrations, run at every start); never edit the tables by hand.

### Encryption at rest

The database holds the content of therapies: enable MariaDB's data-at-rest encryption, and/or encrypt the disk it lives
on. A starting point with the `file_key_management` plugin, to check against the MariaDB documentation for your version:

```ini
[mariadb]
plugin_load_add = file_key_management
file_key_management_filename = /etc/mysql/encryption/keyfile.enc
file_key_management_filekey = FILE:/etc/mysql/encryption/keyfile.key
file_key_management_encryption_algorithm = AES_CTR
innodb_encrypt_tables = FORCE
innodb_encrypt_log = ON
innodb_encrypt_temporary_tables = ON
encrypt_tmp_disk_tables = ON
encrypt_tmp_files = ON
encrypt_binlog = ON
aria_encrypt_tables = ON
```

A key file on the same disk as the data only protects against a stolen disk or a copied data directory; a key
management service (MariaDB has plugins for HashiCorp Vault and the main cloud providers) protects more.

### Backups

Back up the database regularly, encrypt every backup, and keep the keys apart from the backups. Test a restore. Keep
backups no longer than the data they contain may be kept: a couple's data deleted from the database still lives in the
older backups until they expire (section 8).

## 5. Reverse proxy and HTTPS

The proxy:

- terminates TLS with a valid certificate, and redirects every HTTP request to HTTPS;
- passes `X-Forwarded-For` and `X-Forwarded-Proto`. The application trusts these headers only from loopback and private
  addresses (Tomcat's internal proxies). If the proxy reaches the application from another address, set
  `server.tomcat.remoteip.internal-proxies` (a regular expression) in `config/application.properties`;
- keeps the application's port closed to everything else: with the proxy on the same machine, bind the application to
  loopback (`server.address=127.0.0.1`);
- limits login and password-reset attempts per IP address. The application locks an account for 15 minutes after 5
  wrong passwords, but that doesn't stop one password tried on many accounts, and it lets anyone who knows an email lock
  that account: the per-IP limit is the answer to both;
- must not log query strings. The activation and password-reset links (`/attiva?codice=...`,
  `/reimposta-password?codice=...`) carry a one-time code in the address. (The archive's keyword search is a POST, so the
  words people search for never appear in an address.)

The application itself sends `Strict-Transport-Security` (one year, `includeSubDomains`, no `preload`) on HTTPS
requests, `Referrer-Policy: no-referrer`, a `Content-Security-Policy` and a `Permissions-Policy` (described in
`docs/frontend-notes.md`) and Spring Security's other default headers. Don't add a second copy of any of them in the
proxy: a browser applies every Content-Security-Policy it receives, so a second one could only break the app.

The application also says what may be cached: the app's files under `/assets/` for a year (their names change with
their content), everything else never (`no-store`), the API's answers above all, which carry therapy content. Leave
`Cache-Control` as it comes, and don't put a caching layer in the proxy.

Compression belongs to the proxy, and only for the app's files: JavaScript, CSS and SVG (about 760 KB, 190 KB
compressed). Don't compress the API's JSON: compressing over HTTPS answers that mix personal text with what someone else
can influence opens the way to the BREACH attack, for little gain on such small answers.

A minimal nginx example (adapt names, certificates and limits):

```nginx
# In the http block:
limit_req_zone $binary_remote_addr zone=login:10m rate=10r/m;
log_format no_query '$remote_addr - [$time_local] "$request_method $uri" $status $body_bytes_sent';

server {
    listen 80;
    server_name app.example.org;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name app.example.org;
    ssl_certificate     /etc/ssl/app.example.org/fullchain.pem;
    ssl_certificate_key /etc/ssl/app.example.org/privkey.pem;
    access_log /var/log/nginx/aemotional.log no_query;
    # The largest valid request is a message of 20 full paragraphs: about 130 KB in the worst case.
    client_max_body_size 256k;
    # The app's files only: application/json and application/problem+json (the API) are left out on purpose.
    gzip on;
    gzip_proxied any;
    gzip_types text/css application/javascript image/svg+xml;

    location ~ ^/api/auth/(login|password-reset) {
        limit_req zone=login burst=5 nodelay;
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 6. First start and the first administrator

Start the application with the environment of section 3:

```bash
java -jar aemotional-line-0.0.1-SNAPSHOT.jar
```

Nobody can log in yet. The first administrator is created from the server's terminal, with the same environment:

```bash
java -jar aemotional-line-0.0.1-SNAPSHOT.jar --create-admin=admin@example.org --name="Operator"
```

The command starts no web server (it works while the application is running), prints the activation link on the
terminal and exits with code 0; the link is valid for 7 days and once. It is never logged nor emailed: copy it from the
terminal, open it, choose the password. The command refuses (exit code 1) once an administrator is active. If the first
administrator is still waiting for activation, running it again with the same email gives a new link.

The administrator creates and disables therapist accounts, at `/amministrazione`; therapists invite their patients;
nobody can sign up alone. A therapist whose activation link was lost or expired gets a new one from the same page; a
patient still waiting for activation gets a new link with the next invitation.

## 7. Accounts, sessions and logs

- Passwords: 12 to 64 characters, not a common password, not the account's email; stored as bcrypt hashes.
- A login session lasts while the user is active and ends after 30 minutes without requests. Sessions are kept in the
  database (`SPRING_SESSION` tables, cleaned automatically), so they survive a restart. A password reset or the
  disabling of an account ends all its sessions.
- The application logs no personal content: no message texts, justifications, names, emails, passwords or one-time
  codes; at most ids and event types. A test checks it. Keep the log level at INFO in production: DEBUG or TRACE on
  Spring's web or security packages would log request details.
- The position a client reports for a location constraint is used for that check and never stored.
- The centre of a location constraint (a place the therapist enters, possibly a partner's home) is stored, and shown only
  to the therapist and to the partner it binds. The app links to OpenStreetMap to show it: the coordinates reach
  OpenStreetMap only when a user clicks that link; the app embeds no map and loads nothing from it.

## 8. Ending couples, deleting data, retention

- The therapist ends a couple when the therapy ends: its conversation becomes read-only and stays readable by its three
  members.
- The couple's therapist can then delete all its data, confirming with their own password (an administrator can do it
  when the therapist's account is disabled). The deletion is irreversible; only a minimal trace stays (couple id,
  therapist id, end and deletion dates, who deleted it).
- `AEMOTIONAL_RETENTION_AFTER_END` makes the application delete, every night at 03:30 UTC, the data of the couples ended
  longer ago than that period. It is **disabled** until the retention rule is decided (section 9). A wrong value stops
  the application at start, rather than misapplying the rule.

## 9. Before going live: to check with a privacy advisor or DPO

These are legal and organisational decisions, not technical ones; the application only provides the means.

- **Legal basis**: the conversations are likely health data (GDPR art. 9): which basis applies, and whether explicit
  consent is needed.
- **Roles**: who is the controller (the therapist, the operator, both) and which data processing agreements are needed,
  with the hosting provider too (art. 28).
- **Impact assessment**: a DPIA is likely required (art. 35).
- **Retention**: how long a couple's data are kept after the end, given the therapist's professional obligations; then
  set `AEMOTIONAL_RETENTION_AFTER_END`, and align the backups' lifetime.
- **Information**: the privacy notices for therapists and patients.
- **Rights**: how access and erasure requests are handled (today: a patient deletes their own account when not in an
  active couple; the therapist deletes an ended couple's data).
- **Breaches**: the procedure for notifying a breach within 72 hours.
- **Hosting**: where the servers and backups are (inside the EU or with adequate safeguards).
- **Places**: whether the notices must mention that a location constraint's place opens on OpenStreetMap, a third party,
  when the therapist or the partner it binds clicks "Apri su OpenStreetMap".

## 10. Known limits

- A password-reset request for an existing account takes a little longer (it sends an email), so its timing could
  reveal that the account exists; sending emails in the background will remove it.
- No per-IP limit inside the application: it belongs to the proxy (section 5). The same goes for the size of a request
  (`client_max_body_size`): the application has no limit of its own.
- Two-factor authentication and a log of who consulted which conversation are planned, not built.
