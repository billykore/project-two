# Research: Forgot Password

Decisions R1–R12 resolve every open question in the plan's Technical Context. Each lists the decision, the reason,
and the alternatives considered.

R3 (HMAC instead of BCrypt for codes) and R6 (exception translation) were revised on 2026-11-13 after
`/speckit-analyze` (findings A1 and C1).

## R1. Two endpoints: request a code, then confirm with code and new password

- **Decision**: `POST /auth/password-reset/request` takes the contact and always answers `202 Accepted` with the
  same body. `POST /auth/password-reset/confirm` takes the contact, the code, and the new password, and answers
  `204 No Content` on success. There is no separate "verify code" step.
- **Rationale**: the spec's flow (US1) is one submission of code + new password. A verify step would need a second
  secret (a reset token) with its own lifetime, storage, and tests, for no requirement in the spec (Principle V).
  FR-013 keeps the one-step flow friendly: a refused password does not burn the code.
- **Alternatives**: three steps (request → verify → set password with a reset token): better for multi-screen UIs,
  rejected as unneeded. A single `/auth/forgot-password` with a mode flag: rejected, two operations with different
  responses read better as two endpoints.

## R2. The contact is given as `email` or `phone`, exactly one

- **Decision**: both request bodies have optional `email` and `phone` properties; exactly one must be present. The
  value is validated with the registration rules (email trimmed and lowercased, at most 254 characters; phone `+`
  and 8–15 digits). The present property also chooses the delivery channel (spec Assumptions). Field errors name
  `email` or `phone`, exactly as at registration (FR-002). If both or neither are given, the error names both.
- **Rationale**: the channel is explicit, and the field names and messages match registration, so clients reuse
  their handling. Validation stays in one place (R4).
- **Alternatives**: one `contact` property whose type is guessed (`+` → phone, `@` → email): shorter, but an
  input like `0812…` can't be attributed to a field, so the error would be vaguer.

## R3. Codes are hashed with HMAC-SHA256 under a server-side key

- **Decision**: a new outbound port `ResetCodeHasher` with `hash(code)` and `matches(code, hash)`. Its adapter
  `adapter/security/HmacResetCodeHasher` computes HMAC-SHA256 over the UTF-8 bytes of the code with a dedicated key.
  The key is `app.password-reset.code-secret`, read from the new environment variable `APP_RESET_CODE_SECRET` (Base64,
  at least 32 bytes, validated at startup like the JWT secret). The adapter stores the hash as 64 lowercase hex
  characters. `matches` compares in constant time (`MessageDigest.isEqual`). The adapter's properties record
  `ResetCodeHasherProperties` lives in `adapter/security` and is enabled from `config` (constitution IV).
- **Rationale**:
  - **Plain SHA-256 is too weak**: a 6-digit code hashed with SHA-256 (the existing `TokenHasher`) is reversed by
    trying all 10⁶ codes in under a second. With HMAC, someone who reads the database still needs the key, which is
    never stored there, so FR-016 holds.
  - **BCrypt is too slow**: BCrypt at strength 14 would also be safe, but costs about 1 s per hash. That makes
    SC-002 (code handed to the channel within 1 s) unreachable and holds the account lock for seconds. HMAC takes
    microseconds.
  - **Key separation**: the key is separate from the JWT key, so rotating one does not affect the other.
- **Cost**: one new secret to configure. Without it the application fails to start, just as without
  `APP_JWT_SECRET`. Rotating the key invalidates only codes issued in the last 5 minutes.
- **Alternatives**:
  - BCrypt through the existing `PasswordHasher`: no new secret, but it misses SC-002 (the analysis finding A1).
  - Plain SHA-256: rejected (above).
  - Reusing the JWT key: rejected, because one key would then have two purposes.

## R4. Password rules are shared, not copied

- **Decision**: move the password checks out of `RegisterUserCommand` into `core/valueobject/PasswordRules`, a final
  class with static methods. `formatError(password)` covers length, bytes, and presence. `usernameError(password,
  username)` covers equality with the username. `RegisterUserCommand` calls them, with unchanged messages.
  `ResetPasswordCommand` checks the format. The username check runs in the service after the code is verified,
  because the username is only known once the account is found.
- **Rationale**: FR-011 says "the registration password rules". One definition keeps them identical. Registration
  behaviour and messages stay the same; `RegisterUserCommandTest` keeps every case and expected message (it only
  changes mechanically for R5).
- **Contact rules too**: the email and phone format checks are shared in the same way through the package-private
  `core/valueobject/ContactRules`. It holds the patterns, the format messages, and the "exactly one of email or
  phone" check used by both reset commands. `RegisterUserCommand` keeps its own "is required" messages and delegates
  only the format checks, so registration answers exactly as before (FR-002).
- **Alternatives**: copy the checks into the new command: two copies drift apart. A `Password` value object: it
  would change `RegisterUserCommand`'s public shape and its tests for no gain.

## R5. Field errors are shared between registration and reset

- **Decision**: `FieldError` becomes `record FieldError(String field, String message)`, where `field` is the name
  clients send (`RegistrationField.jsonName()` for registration; `email`, `phone`, `code`, `newPassword` for reset).
  A new `InvalidPasswordResetException(List<FieldError>)` mirrors `InvalidRegistrationException`.
- **Rationale**: both features return the same `errors[]` Problem Details shape (FR-018). A second, near-identical
  error record would be duplication. `RegistrationField` stays, because `RegistrationConflictException` needs it.
- **Cost**: a secondary constructor `FieldError(RegistrationField field, String message)` keeps registration call
  sites compiling. `RegisterUserCommandTest` and `RegistrationExceptionsTest` assertions that extract
  `FieldError::field` must compare names (`"password"`) instead of enum values. This is a mechanical type change with
  the same expected values, not a weakened assertion. `InvalidRegistrationException` and
  `RegistrationExceptionHandler` drop their `.jsonName()` calls. The HTTP responses are byte-for-byte unchanged.
- **Alternatives**: a `PasswordResetField` enum and a second error record: duplicated. A generic `Field` interface
  implemented by both enums: more ceremony than a string.

## R6. A row lock on the account serializes every reset operation for that account

- **Decision**: both use cases load the account through `UserRepository.findForPasswordReset(contact)`. It takes a
  pessimistic write lock on the `users` row (`@Lock(PESSIMISTIC_WRITE)` in the Spring Data query) and holds it until
  the transaction ends. Under that lock:
  - at most one code is open per account (FR-006), also backed by a partial unique index (R8);
  - the resend wait and hourly limit counts can't be raced (FR-008);
  - wrong-attempt counting can't be raced past 5 (FR-007);
  - a code is accepted at most once even when submitted twice at the same moment (FR-017).
- **Rationale**: one lock on one row covers four concurrency rules with no version columns or retries. Contention
  is per account, and only reset operations take it. Logins and other readers are not blocked.
- **Caveat to verify in the integration test**: Hibernate's PostgreSQL dialect renders `PESSIMISTIC_WRITE` as
  `FOR NO KEY UPDATE`. That lets inserts referencing the user (sessions, trips) proceed. If it ever rendered
  `FOR UPDATE` instead, those inserts would wait for at most one reset transaction (a few seconds). That is
  acceptable, but the test pins the behavior.
- **Exception translation (constitution VII)**: two infrastructure failures can still occur, and both are translated
  in the adapter before they cross the port, into the domain exception `PasswordResetBusyException`:
  - **Lock not acquired in time**: Spring's `PessimisticLockingFailureException` (including
    `CannotAcquireLockException`), raised when another reset on the same account holds the lock longer than
    PostgreSQL's `lock_timeout`. The adapter sets it per transaction with `SET LOCAL` (default 15 s,
    `app.password-reset.lock-timeout`). It does not use the JPA hint `jakarta.persistence.lock.timeout`, because
    Hibernate's PostgreSQL dialect does not reliably apply a positive lock timeout. 15 s stays well above the
    longest confirm (about 4 s of BCrypt). Translated in `JpaUserRepository`.
  - **Second open code**: a `DataIntegrityViolationException` on `uq_password_reset_codes_open`. The lock makes this
    unreachable, but the index is the safety net. Translated in `JpaPasswordResetCodeRepository`.

  Other integrity violations are bugs and are not business outcomes. They are wrapped in an `IllegalStateException`
  with the constraint name, so no Spring or driver type leaves the adapter.

  `PasswordResetBusyException` maps to `503 Service Unavailable` with `Retry-After: 1` and a "try again" detail. It
  only surfaces from `resetPassword`. There it can only happen for an existing account under a concurrent reset.
  That is accepted, because progress still needs a valid code.

  In `requestReset`, surfacing it would break FR-004, so it is handled like a limit: logged, and the caller gets the
  generic `202`. It must not be caught inside the transaction that failed to take the lock, because PostgreSQL aborts
  that transaction on the lock error and the commit would then fail. So the request is split:
  - `PasswordResetService.requestReset` is **not** transactional. It generates and hashes the code, then calls
    `ResetCodeIssuer.issue(...)`.
  - `ResetCodeIssuer` is a second `@Service` in `service`, and it is `@Transactional`. It locks the account, applies
    the limits, supersedes the open code, and stores the new one. It returns the account id (`OptionalLong`), empty
    when no code was issued, so a delivery failure can be logged with the account.
  - Back in `requestReset`, after that transaction has committed or rolled back, the service catches
    `PasswordResetBusyException` and sends the code only if one was issued.

  This keeps transactions on application services (Principle VIII, AR-06). Delivery also now happens after commit,
  which resolves the note in R10.
- **Alternatives**: optimistic locking with a `version` column on the code row: needs retry handling and does not
  cover the rate-limit counts. `SERIALIZABLE` isolation: retries again, and it affects the whole transaction.

## R7. A wrong code must be remembered even though the request fails

- **Decision**: `PasswordResetService.resetPassword` is
  `@Transactional(noRollbackFor = InvalidResetCodeException.class)`. On a wrong code it records the failed attempt
  (and, on the 5th, invalidates the code), then throws. The transaction still commits that change.
- **Rationale**: with the default rollback, the attempt counter would never increase and FR-007 would be unenforceable.
  `InvalidPasswordResetException` (refused new password) keeps the default rollback; nothing has been changed by
  then, and the code stays usable (FR-013).
- **Alternatives**: record the attempt in a separate `REQUIRES_NEW` transaction: a second connection and more
  moving parts for the same result.

## R8. Storage: two new tables in migration V12

- **Decision**: `password_reset_codes` (one row per issued code) and `password_history` (previous password hashes).
  A partial unique index allows only one open code per account. The history keeps only the 2 newest entries per
  account (the current password lives in `users.password`, making 3). Details are in
  [data-model.md](./data-model.md).
- **Rationale**: the hourly limit is a count of codes issued in the last hour, so issued codes must stay
  queryable after use. Pruning history to what FR-012 needs keeps old password hashes no longer than necessary.
- **Alternatives**: columns on `users` for the current code: they can't express "5 per hour". Keeping all history:
  stores old hashes with no use. Clean-up of old code rows (older than an hour) is not needed for correctness and is
  left to a later task; the table grows by at most 5 rows per account per hour.

## R9. Delivery: one outbound port, two development adapters selected by a property

- **Decision**: outbound port `ResetCodeSender.send(Contact contact, String code)`, in `adapter/notification`:
  - `LoggingResetCodeSender`: active when `app.password-reset.log-codes=true`. It writes the channel, address, and
    code to the application log at `INFO` under the logger `org.kore.myapp.adapter.notification`, the
    development delivery the clarification asked for.
  - `UnavailableResetCodeSender`: active otherwise (the default). It sends nothing and throws
    `CodeDeliveryException`, so the failure is recorded (spec edge case) without the code.

  The property defaults to `false` in `application.properties`. `.env.example` sets it to `true` for local use,
  and the tests set it too.
- **Rationale**: production can never log codes by accident: it has to opt in. A real email or SMS provider later is a
  new adapter implementing the same port, with no change to the reset rules (FR-020). The application always starts,
  because exactly one sender bean exists.
- **Alternatives**: a Spring profile `dev`: the project uses no profiles today. Separate `EmailSender` and
  `SmsSender` ports: the application only ever needs "send this code to this contact", and the channel is in
  `Contact`. A real SMTP adapter: out of scope (clarification Q2).

## R10. Delivery failure keeps the generic response

- **Decision**: the service calls `ResetCodeSender.send` after the code is stored and committed. It handles
  `CodeDeliveryException` (a domain exception from `core/exception`) and logs a warning with the account id and
  channel, never the code. The request still completes with the generic `202`, and the code stays stored and counts
  toward the limits. The sender adapters do not log the failure themselves, so it is recorded once.
- **Rationale**: FR-004 requires the same response whether a code was sent or not. Answering `5xx` would also reveal
  that the contact has an account. This is deliberate handling, not swallowing (Principle I): the failure is logged,
  and the business rule FR-004 decides the outcome.
- **Sending after commit**: `requestReset` sends only after `ResetCodeIssuer`'s transaction has committed (R6). A
  slow real provider will therefore never hold a database connection or the account lock, and a code is never sent
  for a row that was rolled back.

## R11. Timing and enumeration

- **Decision**: the request use case generates and hashes a code before looking up the account. With HMAC (R3), this
  work is microseconds, so the remaining timing difference is a few database writes and the send call.
- **Rationale**: SC-005 requires identical content and status, and timing is not in its scope. With HMAC there is no
  expensive step left whose absence would stand out. Hashing first keeps the code paths uniform at no cost.
- **Alternatives**: a fixed artificial delay: wastes time and is less precise.

## R12. Session revocation, configuration, and randomness

- **Sessions**: new port operation `SessionRepository.revokeAllForUser(long userId, Instant at)`. It does a bulk
  `UPDATE sessions SET revoked_at = :at WHERE user_id = :userId AND revoked_at IS NULL`. `Session.revokedAt`'s
  mapping is corrected from `nullable = false` to nullable, to match the V3 column. Issued JWTs are not checked
  against sessions (spec Assumption), so this change only affects session records.
- **Tunables**: `config/PasswordResetProperties` (`app.password-reset.*`): `code-ttl=5m`, `max-attempts=5`,
  `resend-interval=60s`, `max-codes-per-hour=5`. `ApplicationConfig` turns them into the domain record
  `PasswordResetPolicy`, as it already turns `FareProperties` into `FareCalculator`. The code length (6) and
  history size (3) are fixed by the spec, so they are constants.
- **Randomness**: new outbound port `ResetCodeGenerator.newCode()`, wired in `ApplicationConfig` as a lambda over
  one `SecureRandom` (as `IdGenerator` is over `UUID`). It returns `String.format("%06d", random.nextInt(1_000_000))`.
  Tests inject a fixed code (Principles II and VIII).
- **Time**: every instant comes from the injected `Clock` and is passed into domain methods.
