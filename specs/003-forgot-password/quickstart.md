# Quickstart: Validate Forgot Password

**Feature**: [spec.md](./spec.md) | **Contracts**: [contracts/http-api.md](./contracts/http-api.md),
[contracts/ports.md](./contracts/ports.md) | **Data**: [data-model.md](./data-model.md)

## Prerequisites

- Java 25, and a Docker-compatible runtime (for Testcontainers).
- For the manual checks: the app running locally as described in the README, with `.env` containing
  `APP_PASSWORD_RESET_LOG_CODES=true` so codes appear in the application log (research R9), and
  `APP_RESET_CODE_SECRET` (Base64, at least 32 bytes: `openssl rand -base64 32`) for hashing codes (research R3). Flyway applies `V12`
  on startup.

## 1. Automated checks

```bash
./mvnw test
```

Expected: green, including:

| Test | Proves |
|------|--------|
| `PasswordRulesTest` | each password rule and boundary, with the registration messages unchanged |
| `RegisterUserCommandTest` (same cases and messages) | registration behaviour survives the extraction of `PasswordRules` |
| `RequestPasswordResetCommandTest`, `ResetPasswordCommandTest` | exactly one of email/phone; normalization; code `[0-9]{6}` incl. `004213`; all errors at once; masked `toString` |
| `PasswordResetCodeTest` | usable/expired/used/invalidated; 5th failed attempt invalidates; `markUsed` on unusable throws |
| `UserTest` | `changePassword` replaces the hash, rejects blank |
| `PasswordResetServiceTest` | generic path for unknown account, resend wait, hourly limit, superseding, wrong code counted, username and last-3 checks keep the code, success writes history, uses code, revokes sessions; delivery failure still completes |
| `JpaPasswordResetCodeRepositoryIT` | `findOpen`, `countIssuedSince`, partial unique index rejects a second open code |
| `JpaPasswordHistoryRepositoryIT` | newest-first order, `keepNewest(2)` |
| `JpaUserRepositoryIT` | `findForPasswordReset` by email and phone; a second transaction past the lock timeout gets `PasswordResetBusyException`; a session insert for the same user is not blocked |
| `JpaUserAndSessionRepositoryIT` | `revokeAllForUser` ends only open sessions of that user |
| `LoggingResetCodeSenderTest`, `UnavailableResetCodeSenderTest` | log line contains the code; unavailable throws `CodeDeliveryException` and logs no code |
| `HmacResetCodeHasherTest` | deterministic 64-hex output; different keys give different hashes; `matches` true/false; short or missing key rejected |
| `PasswordResetControllerTest` | 202 generic body, 204, 400 `errors`, 400 invalid code, malformed body, no token needed |
| `PasswordResetFlowIT` | register → request → read the code from a recording sender → confirm → new password logs in, old fails; two simultaneous confirms with one code → exactly one 204; 6 wrong codes → correct code refused |
| `ArchitectureTest` | AR-01..AR-09 still pass, including the new `adapter/notification` package |

## 2. Manual end-to-end (app running on :8080)

Register (or reuse) an account:

```bash
curl -s -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"username":"budi","password":"secret123","name":"Budi","email":"budi@example.com","phone":"+6281234567890"}'
```

Request a code (expect `202` and the generic message):

```bash
curl -i -X POST http://localhost:8080/auth/password-reset/request -H 'Content-Type: application/json' -d '{"email":"Budi@Example.com"}'
```

Find the code in the application log (a line from `org.kore.myapp.adapter.notification` with `EMAIL budi@example.com`).
Then confirm (expect `204`):

```bash
curl -i -X POST http://localhost:8080/auth/password-reset/confirm -H 'Content-Type: application/json' -d '{"email":"budi@example.com","code":"<code>","newPassword":"newsecret99"}'
```

Log in with the new password (expect `200`) and the old one (expect `401`):

```bash
curl -i -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"username":"budi","password":"newsecret99"}'
curl -i -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"username":"budi","password":"secret123"}'
```

Further checks:

- Unknown contact `{"email":"nobody@example.com"}` gives the same `202` and body, and nothing is logged by the
  sender.
- A second request within 60 s gives `202`, but no new code is logged.
- Reusing the code from the first confirm gives `400 Invalid or expired code`.
- A new code confirmed with `"newPassword":"secret123"` (the previous password) gives `400` with
  `newPassword: must not be one of your last 3 passwords`. Then the same code with a fresh password gives `204`.
- Requesting by `{"phone":"+6281234567890"}` logs a `PHONE` delivery.

## 3. Data checks

```sql
SELECT channel, failed_attempts, used_at IS NOT NULL AS used, invalidated_at IS NOT NULL AS invalidated,
       code_hash ~ '^[0-9a-f]{64}$' AS is_hmac_hex
FROM password_reset_codes c JOIN users u ON u.id = c.user_id WHERE u.username = 'budi' ORDER BY issued_at;

SELECT count(*) FROM password_history h JOIN users u ON u.id = h.user_id WHERE u.username = 'budi';  -- at most 2

SELECT count(*) FROM sessions s JOIN users u ON u.id = s.user_id
WHERE u.username = 'budi' AND s.revoked_at IS NULL AND s.created_at < now() - interval '1 minute';  -- 0 after a reset
```

Grep the application log for `newsecret99`: there should be no matches. Codes should appear only on lines from the
development sender (SC-007, research R9).

## 4. Production-safety check

Start the app without `APP_PASSWORD_RESET_LOG_CODES` (or set it to `false`) and request a code. The response is
still `202`, a warning says delivery is unavailable, and no code appears anywhere in the log.

## 5. Code hand-off time (SC-002)

With `APP_PASSWORD_RESET_LOG_CODES=true`, register 20 accounts, then send one reset request for each, one at a time,
and print each response time:

```bash
for i in $(seq 1 20); do curl -s -o /dev/null -w '%{http_code} %{time_total}\n' -X POST http://localhost:8080/auth/password-reset/request -H 'Content-Type: application/json' -d "{\"email\":\"perf$i@example.com\"}"; done
```

The response returns after the code has been handed to the sender (research R6), so the response time bounds the
hand-off time. Expected: every line starts with `202`, and at least 19 of 20 times are under `1.0` seconds. Record the
slowest time in the pull request. Codes are hashed with HMAC (research R3), so the hand-off should take a few
milliseconds.
