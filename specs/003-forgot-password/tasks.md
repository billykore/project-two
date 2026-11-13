---

description: "Task list for Forgot Password"
---

# Tasks: Forgot Password

**Input**: Design documents from `/specs/003-forgot-password/`

**Prerequisites**: plan.md, spec.md, research.md (R1–R12), data-model.md, contracts/http-api.md, contracts/ports.md

**Tests**: Test tasks are MANDATORY (constitution Principles II and III). Each story lists its tests first; write
them and see them fail before the implementation tasks.

**Organization**: Tasks are grouped by user story. Tests live under `src/test/java/org/kore/myapp/`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 = reset with a one-time code (P1), US2 = codes cannot be guessed, reused, or abused (P1),
  US3 = new password rules and last-3 history (P2), US4 = reset ends all sessions (P3)

## Path Conventions

- Main code: `src/main/java/org/kore/myapp/...` (written `main/` below); tests: `src/test/java/org/kore/myapp/...`
  (written `test/` below); migrations: `src/main/resources/db/migration/`
- Hexagonal rule (constitution IV): `core` imports no other application package; `api` and `adapter` reach the
  application only through `core/port`. Domain code gets time and codes as arguments (VIII).
- A port method is added in the same task group as the adapter that implements it, so the code compiles at every
  step.

---

## Phase 1: Setup

- [X] T001 Create branch `003-forgot-password` from `main`, then confirm a green baseline by running `./mvnw test` (needs Docker); fix nothing, just record that all tests, including `ArchitectureTest`, pass before changes.

---

## Phase 2: Foundational (blocks all user stories)

**Purpose**: shared domain types, the refactor of the registration password rules and field errors, the schema, and
configuration. Registration behaviour and HTTP responses must not change (plan, R4, R5).

- [X] T002 Change `FieldError` in `main/core/valueobject/FieldError.java` to `record FieldError(String field, String message)` and add a secondary constructor `FieldError(RegistrationField field, String message)` that stores `field.jsonName()`. Update `main/core/exception/InvalidRegistrationException.java` (message uses `e.field()` directly) and `main/api/handler/RegistrationExceptionHandler.java` (drop `.jsonName()`). Update the assertions in `test/core/valueobject/RegisterUserCommandTest.java` and `test/core/exception/RegistrationExceptionsTest.java` that compare or extract `FieldError::field` so they compare names (`"password"`) instead of enum values; keep every case and expected message.
- [X] T003 [P] Write `test/core/valueobject/PasswordRulesTest.java`: `formatError` returns null for 8 and 72 ASCII characters; `is required` for null/empty; `must be at least 8 characters` for 7; `must be at most 72 characters` for 73; the bytes message for 40 × `é`; `usernameError` returns `must not be the same as the username` when equal and null otherwise; `HISTORY_SIZE` is 3.
- [X] T004 Create `main/core/valueobject/PasswordRules.java` (final class, private constructor, static `formatError(String)` and `usernameError(String, String)`, constant `HISTORY_SIZE = 3`) by moving the password checks and messages out of `main/core/valueobject/RegisterUserCommand.java`, and make `RegisterUserCommand` call it. Run `RegisterUserCommandTest` and `PasswordRulesTest`; both must pass with unchanged messages.
- [X] T005 [P] Create `main/core/valueobject/ContactChannel.java` (enum `EMAIL`, `PHONE`) and `main/core/valueobject/Contact.java` (`record Contact(ContactChannel channel, String address)`), per data-model.md.
- [X] T006 [P] Create `main/core/exception/InvalidPasswordResetException.java` (like `InvalidRegistrationException`, holds `List<FieldError>`, message `field: message; …`), `main/core/exception/InvalidResetCodeException.java` (fixed message `Invalid or expired code`), `main/core/exception/CodeDeliveryException.java` (message plus cause constructor), and `main/core/exception/PasswordResetBusyException.java` (fixed message `Another password reset for this account is in progress; try again`, optional cause; research R6).
- [X] T007 [P] Create `main/core/valueobject/PasswordResetPolicy.java` (`record PasswordResetPolicy(Duration codeTtl, int maxAttempts, Duration resendInterval, int maxCodesPerHour)`, constructor rejects null or non-positive values) and `test/core/valueobject/PasswordResetPolicyTest.java` covering valid and each invalid argument.
- [X] T008 [P] Create `main/config/PasswordResetProperties.java` (`@Validated @ConfigurationProperties(prefix = "app.password-reset")` record with `codeTtl`, `maxAttempts`, `resendInterval`, `maxCodesPerHour`; not `logCodes`, because that switch is read only by `@ConditionalOnProperty` on the senders in `adapter/notification`, research R9), add to `src/main/resources/application.properties` exactly these lines, each key once: `app.password-reset.code-ttl=5m`, `app.password-reset.max-attempts=5`, `app.password-reset.resend-interval=60s`, `app.password-reset.max-codes-per-hour=5`, `app.password-reset.log-codes=${APP_PASSWORD_RESET_LOG_CODES:false}`, and `app.password-reset.code-secret=${APP_RESET_CODE_SECRET}`. Do not add a separate `log-codes=false` line; the placeholder default already makes it false. Add `APP_PASSWORD_RESET_LOG_CODES=true` and an empty `APP_RESET_CODE_SECRET=` (with the comment `Base64, at least 32 bytes. Generate with: openssl rand -base64 32`) to `.env.example`, and add a test-only Base64 value of at least 32 bytes for `APP_RESET_CODE_SECRET` to `src/test/resources/config/application.properties`, next to `APP_JWT_SECRET`.
- [X] T009 Edit `main/config/ApplicationConfig.java`: enable `PasswordResetProperties`, add a `PasswordResetPolicy` bean built from it, and add a `ResetCodeGenerator` bean over one `SecureRandom` returning `String.format("%06d", random.nextInt(1_000_000))`. Create the port `main/core/port/ResetCodeGenerator.java` first (`@FunctionalInterface`, `String newCode()`, documented per contracts/ports.md). Add `test/config/ApplicationConfigTest.java` asserting the generated code is always 6 digits over 1,000 draws.
- [X] T010 [P] Write `test/adapter/security/HmacResetCodeHasherTest.java` (plain unit test, no Spring): `hash` is deterministic and returns 64 lowercase hex characters; the same code under two different keys gives different hashes; `matches` is true for the right code and false for a wrong code or a malformed hash; constructing with a missing key, a non-Base64 key, or a key shorter than 32 bytes throws `IllegalStateException`.
- [X] T011 Create the port `main/core/port/ResetCodeHasher.java` (`hash`, `matches`, Javadoc per contracts/ports.md), the record `main/adapter/security/ResetCodeHasherProperties.java` (`@ConfigurationProperties(prefix = "app.password-reset")` with `codeSecret`, like `JwtProperties`), and `main/adapter/security/HmacResetCodeHasher.java` (`@Component`; decodes and validates the key in its constructor; HMAC-SHA256 via `javax.crypto.Mac` over the UTF-8 bytes of the code; lowercase hex via `HexFormat`; `matches` uses `MessageDigest.isEqual`). Enable `ResetCodeHasherProperties` in `main/config/SecurityConfig.java`. Make T010 pass and confirm the application context still starts (`MyAppTests`).
- [X] T012 Create the migration `src/main/resources/db/migration/V12__create_password_reset_tables.sql` exactly as in data-model.md: tables `password_reset_codes` and `password_history`, `code_hash CHAR(64)` (HMAC hex, research R3), the check constraints, `idx_password_reset_codes_user_issued`, the partial unique index `uq_password_reset_codes_open`, and `idx_password_history_user_replaced`. Do not edit any earlier migration.

**Checkpoint**: `./mvnw test` is green; registration responses are unchanged.

---

## Phase 3: User Story 1 - Reset a forgotten password with a one-time code (Priority: P1) 🎯 MVP

**Goal**: request a code by email or phone, receive it through the development sender, send it with a new password,
and log in with the new password.

**Independent Test**: register an account, request a reset by email, read the code from the recording sender, confirm
with a new valid password; the new password logs in and the old one fails. Repeat by phone.

### Tests for User Story 1 (write first, see them fail)

- [X] T013 [P] [US1] Write `test/core/valueobject/RequestPasswordResetCommandTest.java`: email only and phone only are accepted; the email is stripped and lowercased and yields `Contact(EMAIL, …)`; the phone yields `Contact(PHONE, …)`; both given and neither given produce errors on `email` and `phone` with `provide either email or phone`; `must be a valid email address` for `budi@`; the phone format message for `081234567890` and `+62 812-3456-7890`; a blank value counts as absent.
- [X] T014 [P] [US1] Write `test/core/valueobject/ResetPasswordCommandTest.java`: same contact rules; `code` accepts `004213` and rejects `12345`, `1234567`, `12 456`, `abc123`, null with `must be 6 digits`; `newPassword` uses `PasswordRules.formatError`; several invalid fields are all reported; `toString` contains neither the code nor the password.
- [X] T015 [P] [US1] Write `test/core/entity/PasswordResetCodeTest.java` for the happy path: `issue` sets `issuedAt`, `expiresAt = now + ttl`, zero attempts, no `usedAt`; `isUsable` is true before expiry and false at and after `expiresAt`; `markUsed` sets `usedAt`, after which `isUsable` is false; `markUsed` on an expired or used code throws `IllegalStateException`.
- [X] T016 [P] [US1] Add to `test/core/entity/UserTest.java`: `changePassword` replaces the hash; null or blank hash throws `IllegalArgumentException`.
- [X] T017 [P] [US1] Write `test/service/PasswordResetServiceTest.java` (Mockito test doubles for the ports, fixed `Clock`, fixed `ResetCodeGenerator`, a stub `ResetCodeHasher`; build a real `ResetCodeIssuer` from the mocked repositories and pass it to `PasswordResetService`) for the US1 paths: `requestReset` for a known email hashes the code with `ResetCodeHasher`, stores a `PasswordResetCode` with channel EMAIL, and calls `ResetCodeSender.send(contact, code)` after `ResetCodeIssuer.issue` has returned; for a phone contact the channel is PHONE; for an unknown contact nothing is stored or sent and nothing is thrown; a `CodeDeliveryException` from the sender is handled: logged with the user id and channel (no code), and `requestReset` still returns normally; when the issuer throws `PasswordResetBusyException`, `requestReset` returns normally and sends nothing; when `findForPasswordReset` throws it in `resetPassword`, it propagates and nothing is changed. `resetPassword` with the right code on an email contact changes the password hash, marks the code used and saves the user; the same works with the phone contact when the code was sent by email (and vice versa); no matching user, no open code, an expired code, and a wrong code each throw `InvalidResetCodeException` and leave the password unchanged.
- [X] T018 [P] [US1] Write `test/adapter/persistence/JpaPasswordResetCodeRepositoryIT.java` (`@PersistenceAdapterTest`): `add` then `findOpen` returns the code; `findOpen` ignores used and invalidated codes; `countIssuedSince` counts by user and time; a second open code for the same user is rejected by `add` with `PasswordResetBusyException` (translated from the partial unique index), while another code is allowed after the first is used; an insert breaking another constraint (for example `failed_attempts = -1` through a native insert helper, or an unknown `user_id`) surfaces as `IllegalStateException` naming the constraint, never as a Spring or driver exception (constitution VII). Extend `test/adapter/persistence/PersistenceFixtures.java` with helpers to insert a user with email and phone and a reset code.
- [X] T019 [P] [US1] Add to `test/adapter/persistence/JpaUserRepositoryIT.java`: `findForPasswordReset` finds a user by email and by phone and returns empty for an unknown address or for an account with neither; `update` persists a changed password; while one transaction holds the lock (coordinate the threads with `CountDownLatch`, never `sleep`), a second transaction calling `findForPasswordReset` for the same user throws `PasswordResetBusyException` once the lock timeout passes (set `app.password-reset.lock-timeout=500ms` for this test class with `@TestPropertySource`, so the test exercises the production `SET LOCAL` path rather than one of its own; assert the exception arrives after at least 500 ms and well under the default); meanwhile inserting a session for that user in a third transaction is not blocked (research R6 caveat).
- [X] T020 [P] [US1] Write `test/adapter/notification/LoggingResetCodeSenderTest.java` (captures log output: the line has channel, address, and code) and `test/adapter/notification/UnavailableResetCodeSenderTest.java` (throws `CodeDeliveryException`, logs no code).
- [X] T021 [P] [US1] Write `test/api/controller/PasswordResetControllerTest.java` (`@WebMvcTest`, mocked `PasswordResetUseCase`): `request` returns `202` and the generic message for email and phone; `400` Problem Details with `errors[]` for an invalid contact, for both, and for neither; malformed or missing body gives `400 Malformed request`; `confirm` returns `204` with no body; `400` `errors[]` for a bad code format and a short password; `InvalidResetCodeException` gives `400` titled `Invalid or expired code`; `PasswordResetBusyException` from `resetPassword` gives `503` titled `Password reset busy` with header `Retry-After: 1`; no `Authorization` header is needed; `ConfirmPasswordResetRequest.toString()` contains neither the code nor the password.
- [X] T022 [US1] Write `test/PasswordResetFlowIT.java` (`@SpringBootTest` with Testcontainers, following `RegistrationFlowIT`; leave `app.password-reset.log-codes` at its default `false` and replace the sender with a recording test double declared through `@TestBean` or a `@TestConfiguration` bean marked `@Primary`, so exactly one `ResetCodeSender` is injected; assert in a first test that the injected sender is the recording one): register `budi`, request by email, read the recorded code, confirm with `newsecret99`, then login succeeds with the new password and fails with the old (`401`); repeat by phone; request for an unknown email returns the same `202` body as a known one.

### Implementation for User Story 1

- [X] T023 [US1] Create `main/core/valueobject/RequestPasswordResetCommand.java` and `main/core/valueobject/ResetPasswordCommand.java` per data-model.md (validation and normalization in the compact constructor, `contact()` accessor, masked `toString`, `InvalidPasswordResetException` listing every invalid field). Make T013 and T014 pass.
- [X] T024 [US1] Create `main/core/entity/PasswordResetCode.java` (JPA mapping per data-model.md; `issue`, `isUsable`, `markUsed`; leave `recordFailedAttempt` and `invalidate` for US2) and add `User.changePassword(String)` to `main/core/entity/User.java`. Make T015 and T016 pass.
- [X] T025 [US1] Create the ports `main/core/port/PasswordResetUseCase.java`, `main/core/port/PasswordResetCodeRepository.java` (`add`, `update`, `findOpen`, `countIssuedSince`), and `main/core/port/ResetCodeSender.java`, with the Javadoc and `@throws` from contracts/ports.md. Add `findForPasswordReset(Contact)` and `update(User)` to `main/core/port/UserRepository.java`.
- [X] T026 [US1] Implement persistence: `main/adapter/persistence/SpringDataPasswordResetCodeRepository.java` and `JpaPasswordResetCodeRepository.java`; in `SpringDataUserRepository.java` add `findLockedByEmail` and `findLockedByPhone` annotated `@Lock(LockModeType.PESSIMISTIC_WRITE)`, with no timeout hint; create `main/adapter/persistence/PasswordResetLockProperties.java` (`@ConfigurationProperties(prefix = "app.password-reset")` with `Duration lockTimeout`, enabled from `config/ApplicationConfig`) and add `app.password-reset.lock-timeout=15s` to `application.properties`; implement `findForPasswordReset` (run the native `SET LOCAL lock_timeout = '<millis>ms'` through the `EntityManager` first, then choose the locked query by channel; catch `PessimisticLockingFailureException` and throw `PasswordResetBusyException` with it as cause) and `update` in `JpaUserRepository.java`. In `JpaPasswordResetCodeRepository.add`, use `saveAndFlush` and translate `DataIntegrityViolationException`: constraint `uq_password_reset_codes_open` becomes `PasswordResetBusyException`, any other becomes `IllegalStateException` naming the constraint. Make T018 and T019 pass.
- [X] T027 [P] [US1] Create `main/adapter/notification/LoggingResetCodeSender.java` (`@Component`, `@ConditionalOnProperty(name = "app.password-reset.log-codes", havingValue = "true")`, logs channel, address and code at INFO) and `main/adapter/notification/UnavailableResetCodeSender.java` (`@ConditionalOnProperty(… havingValue = "false", matchIfMissing = true)`, throws `CodeDeliveryException` without logging; the service records the failure once). Make T020 pass.
- [X] T028 [US1] Create `main/service/ResetCodeIssuer.java` (`@Service`, `@RequiredArgsConstructor`, `@Transactional` on `boolean issue(Contact, String codeHash, Instant now)`) and `main/service/PasswordResetService.java` (`@Service`, `@RequiredArgsConstructor`), following contracts/ports.md for the US1 steps only. `requestReset` is **not** transactional: generate a code, hash it with `ResetCodeHasher`, call `resetCodeIssuer.issue` (which looks up the account via `findForPasswordReset`, then issues and stores the code), handle `PasswordResetBusyException` by logging and returning, and only if a code was issued send it, handling `CodeDeliveryException` by logging the user id and channel (never the code). `resetPassword` is `@Transactional`: look up the account and the open code, check the code with `ResetCodeHasher.matches`, throw `InvalidResetCodeException` when no account, no usable code or no match, then change the password hash, mark the code used and update both. Make T017 pass.
- [X] T029 [P] [US1] Create `main/api/request/RequestPasswordResetRequest.java`, `main/api/request/ConfirmPasswordResetRequest.java` (masked `toString`), `main/api/response/PasswordResetRequestedResponse.java` (the generic message), `main/api/controller/PasswordResetController.java` (`/auth/password-reset/request` returns `202`, `/confirm` returns `204`, each builds its command and calls one use case), and `main/api/handler/PasswordResetExceptionHandler.java` (`InvalidPasswordResetException` → `400` titled `Invalid password reset` with `errors[]`; `InvalidResetCodeException` → `400` titled `Invalid or expired code`; `PasswordResetBusyException` → `503` titled `Password reset busy` with `Retry-After: 1`). Make T021 and T022 pass.

**Checkpoint**: User Story 1 works on its own: forgot → code → new password → login.

---

## Phase 4: User Story 2 - Codes cannot be guessed, reused, or abused (Priority: P1)

**Goal**: 5-attempt limit, expiry, single use, only the newest code works, no account enumeration, 60-second and
hourly limits.

**Independent Test**: wrong code 5 times then the right one is refused; an expired or used code is refused; a new
request invalidates the earlier code; an unknown email gets the identical response; limits stop extra codes.

### Tests for User Story 2 (write first, see them fail)

- [X] T030 [P] [US2] Extend `test/core/entity/PasswordResetCodeTest.java`: `recordFailedAttempt` increments and keeps the code usable below the maximum; the 5th failure sets `invalidatedAt` and `isUsable` becomes false; `recordFailedAttempt` on an unusable code throws `IllegalStateException`; `invalidate` closes an open code, and does nothing to a used or already-invalidated one.
- [X] T031 [P] [US2] Extend `test/service/PasswordResetServiceTest.java`: a request invalidates the account's previous open code before storing the new one; a request within the resend interval (at the boundary: 59 s refused, 60 s allowed) sends and stores nothing and returns normally; the 6th request within an hour sends nothing, the 5th is allowed; a wrong code calls `recordFailedAttempt` with `policy.maxAttempts`, updates the code, and throws `InvalidResetCodeException`; after the 5th wrong code the right code is refused; a used code and a superseded code are refused; the unknown-account request still calls the generator and hasher exactly once (verify with Mockito) so the work is equal (R11); verify with reflection or the annotation that `resetPassword` is `@Transactional(noRollbackFor = InvalidResetCodeException.class)`.
- [X] T032 [US2] Extend `test/PasswordResetFlowIT.java`: five wrong codes then the correct one is refused; a code is refused after the clock passes five minutes (use a settable `Clock` test bean); an old code is refused after a new one is requested; a second request within 60 seconds does not change the recorded code; two concurrent confirms with the same code (started together with a `CountDownLatch`, each with a different new password) produce exactly one `204`, and the other produces `400 Invalid or expired code` or `503 Password reset busy`; afterwards exactly one of the two new passwords logs in, the code row has `used_at` set, and `password_history` gained exactly one entry; the `400` bodies for a wrong, expired, used, and unknown-account code are identical; a wrong code survives the failed request (the `failed_attempts` column is 1 afterwards).

### Implementation for User Story 2

- [X] T033 [US2] Add `recordFailedAttempt(Instant now, int maxAttempts)` and `invalidate(Instant now)` to `main/core/entity/PasswordResetCode.java`. Make T030 pass.
- [X] T034 [US2] Extend `main/service/ResetCodeIssuer.java` and `main/service/PasswordResetService.java`: inject `PasswordResetPolicy` into both; in `ResetCodeIssuer.issue` apply the resend interval and hourly cap through `countIssuedSince` (return `false` when a limit applies), and invalidate and update the open code before issuing the new one; in `PasswordResetService.resetPassword` record a failed attempt on a wrong code, update the code, then throw; change the annotation to `@Transactional(noRollbackFor = InvalidResetCodeException.class)`. Make T031 and T032 pass.
- [X] T035 [US2] Add the concurrency check to `test/adapter/persistence/JpaPasswordResetCodeRepositoryIT.java`: two threads inserting an open code for the same user at the same time (coordinated with `CountDownLatch`) result in exactly one success and one `PasswordResetBusyException` (the partial unique index, translated), proving the safety net behind R6.

**Checkpoint**: Stories 1 and 2 both work; the reset flow is safe against guessing and reuse.

---

## Phase 5: User Story 3 - New password follows the registration rules and is not recently used (Priority: P2)

**Goal**: the new password obeys the registration rules and differs from the last 3 passwords (the current one
plus the 2 before it); a refused password keeps the code usable.

**Independent Test**: after two resets, each of the last 3 passwords is refused, a fourth is accepted, and the
oldest becomes allowed again.

### Tests for User Story 3 (write first, see them fail)

- [X] T036 [P] [US3] Write `test/core/entity/PasswordHistoryEntryTest.java`: `of` copies user id, hash and time; blank hash or null time is rejected.
- [X] T037 [P] [US3] Extend `test/service/PasswordResetServiceTest.java`: a new password equal to the username throws `InvalidPasswordResetException` on `newPassword` and leaves the code open (not used, no attempt counted, nothing saved); a new password matching the current hash is refused with `must not be one of your last 3 passwords`; one matching either of the 2 hashes from `findRecentHashes(userId, 2)` is refused; a password matching none succeeds, adds the old hash to history and calls `keepNewest(userId, 2)`; with fewer than 2 history entries only the known ones are compared; the username and history checks are not reached for a wrong code (history repository is never queried).
- [X] T038 [P] [US3] Write `test/adapter/persistence/JpaPasswordHistoryRepositoryIT.java`: `findRecentHashes` returns newest first and honours the limit; `keepNewest(userId, 2)` keeps only the 2 newest and leaves other users' entries; ties on `replaced_at` are broken by id.
- [X] T039 [US3] Extend `test/PasswordResetFlowIT.java`: register with `pass-one-1`, reset to `pass-two-2`, then to `pass-three-3`; resets back to each of the three are refused with `newPassword: must not be one of your last 3 passwords` and the same code then works with `pass-four-4`; afterwards `pass-one-1` is accepted by a further reset; a too-short password and a password equal to the username return `400` with `errors[]` on `newPassword` and the code still works for an acceptable password.

### Implementation for User Story 3

- [X] T040 [US3] Create `main/core/entity/PasswordHistoryEntry.java` (JPA mapping, static `of(long, String, Instant)`) and the port `main/core/port/PasswordHistoryRepository.java` (`add`, `findRecentHashes`, `keepNewest`), then `main/adapter/persistence/SpringDataPasswordHistoryRepository.java` (including the native `DELETE … WHERE id NOT IN (…)` for `keepNewest`) and `JpaPasswordHistoryRepository.java`. Make T036 and T038 pass.
- [X] T041 [US3] Extend `main/service/PasswordResetService.java` after the code check: reject a new password equal to the username with `PasswordRules.usernameError`; compare the new password with the current hash and with `findRecentHashes(userId, PasswordRules.HISTORY_SIZE - 1)` using `PasswordHasher.compare`; throw `InvalidPasswordResetException` with the `must not be one of your last 3 passwords` message; on success add the replaced hash to history and call `keepNewest`. Make T037 and T039 pass.

**Checkpoint**: Stories 1–3 work; password reuse and weak passwords are refused without burning the code.

---

## Phase 6: User Story 4 - Resetting signs out everywhere (Priority: P3)

**Goal**: a successful reset ends all of the account's sessions.

**Independent Test**: log in twice as `budi`, reset the password, and see both sessions recorded as ended; a new
login starts a new session.

### Tests for User Story 4 (write first, see them fail)

- [X] T042 [P] [US4] Extend `test/adapter/persistence/JpaUserAndSessionRepositoryIT.java`: `revokeAllForUser` sets `revoked_at` on every open session of that user and returns the count, leaves already-revoked sessions' timestamps and other users' sessions untouched; a session saved with a null `revokedAt` loads correctly (mapping fix).
- [X] T043 [P] [US4] Extend `test/service/PasswordResetServiceTest.java`: a successful reset calls `sessionRepository.revokeAllForUser(userId, now)` once; a refused password and a wrong code never call it.
- [X] T044 [US4] Extend `test/PasswordResetFlowIT.java`: log in twice, reset, then both session rows have `revoked_at` set; a fresh login afterwards creates a session with `revoked_at` null.

### Implementation for User Story 4

- [X] T045 [US4] Change the mapping of `revokedAt` in `main/core/entity/Session.java` to nullable (matching column `sessions.revoked_at` from V3), add `int revokeAllForUser(long userId, Instant at)` with Javadoc to `main/core/port/SessionRepository.java`, implement it with a `@Modifying` JPQL bulk update in `SpringDataSessionRepository.java` and `JpaSessionRepository.java`. Make T042 pass and confirm `AuthServiceTest` still passes.
- [X] T046 [US4] Call `sessionRepository.revokeAllForUser(userId, now)` as the last step of `resetPassword` in `main/service/PasswordResetService.java` (inject `SessionRepository`). Make T043 and T044 pass.

**Checkpoint**: all four stories work.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T047 Update `README.md`: add the two endpoints to the API table with request and response examples; a "Forgot password" section including the development-sender note; the `app.password-reset.*` properties, `APP_PASSWORD_RESET_LOG_CODES`, and the required secret `APP_RESET_CODE_SECRET` (with the `openssl rand -base64 32` hint) to Configuration; the new tables to the Database schema section; the new tests to Testing; and the feature to Feature status.
- [X] T048 [P] Add an entry under **Unreleased** in `CHANGELOG.md`: added forgot-password endpoints, `V12` migration, configuration, sessions revoked on reset; changed: password rules shared with registration (no behaviour change).
- [X] T049 [P] Confirm `test/ArchitectureTest.java` passes with the new `adapter/notification` package and the new ports (AR-01..AR-09), with no changes to the rules; if a rule needs a justified exception, record it in plan.md Complexity Tracking.
- [ ] T050 Run the quickstart: `./mvnw test` (everything green, no new compiler warnings), then the manual steps in `specs/003-forgot-password/quickstart.md` sections 2–5 against a local app, recording the slowest request time for SC-002 (section 5) in the pull request, including the production-safety check with `log-codes` off; grep the application log for the test passwords to confirm none appear (SC-007).
- [X] T051 [P] Final pass over the diff against the constitution: no unused imports, no dead code, no commented-out code, constructor injection only, and no `Instant.now()`/`UUID.randomUUID()` in `core` or `service`.

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2 → stories**. Phase 2 blocks everything; inside it T002 → T004 (T003 first), T009 needs the T007 policy and T008 properties, T011 needs T010 and the T008 secret entries, and T012 is independent.
- **US1** is the MVP and blocks the rest, because US2–US4 extend its service, entity, and flow test. Inside US1: tests T013–T022 first, then T023 → T024 → T025 → T026 (needs T012) and T027 → T028 → T029.
- **US2** needs US1. **US3** and **US4** each need US1 but not each other; both edit `PasswordResetService.java` and `PasswordResetFlowIT.java`, so do them one after the other (US3 then US4 by priority), not at the same time.
- **Polish** needs all stories.

## Parallel Opportunities

- Phase 2: T003, T005, T006, T007, T008, and T010 touch different files and can run together; T012 can too.
- US1 tests: T013–T021 are all different files, so they can be written in parallel; T027 and T029 can be built in parallel with persistence work.
- US2: T030 and T031 in parallel. US3: T036, T037, T038 in parallel. US4: T042 and T043 in parallel.
- Polish: T048, T049, and T051.

## Implementation Strategy

- **MVP first**: Phases 1–3 (T001–T029) give a working reset for development use. Do not ship to production before
  US2: US1 alone has no attempt limit or rate limit. Stop and validate with the US1 independent test.
- **Incremental**: add US2 (safety), then US3 (password policy), then US4 (sessions), validating each checkpoint with
  `./mvnw test` and the quickstart.
- **Inside-out within each story** (constitution workflow): domain and its tests → ports → service and its tests →
  persistence adapters and integration tests → controller and web-layer tests → flow test.
- **Scope note**: real email and SMS providers are out of scope (spec clarification); without `log-codes=true` the
  feature cannot deliver a code, by design.

---

## Phase 8: Convergence

- [X] T052 CRITICAL: translate infrastructure exceptions in `main/adapter/persistence/JpaPasswordResetCodeRepository.java` (`update`) and `main/adapter/persistence/JpaPasswordHistoryRepository.java` (`add`). Catch `DataIntegrityViolationException` from `saveAndFlush` and throw `IllegalStateException` naming the constraint, as `JpaPasswordResetCodeRepository.add` already does. Share the constraint-name lookup rather than copying it. Add cases to `test/adapter/persistence/JpaPasswordResetCodeRepositoryIT.java` and `test/adapter/persistence/JpaPasswordHistoryRepositoryIT.java` (for example, a history entry for an unknown `user_id`). Per Constitution VII and plan R6 (contradicts).
- [X] T053 Verify that a token sent to `/auth/password-reset/request` and `/auth/password-reset/confirm` is ignored. Add `test/api/controller/PasswordResetControllerTest.java` cases with a valid Bearer token and with an expired or invalid one; all of them must get the normal `202`, `204`, or `400` answers. If an invalid or expired token is answered `401`, configure `main/config/SecurityConfig.java` so `/auth/**` does not resolve Bearer tokens (for example, a `BearerTokenResolver` that returns null for those paths). Then confirm `AuthControllerTest` still passes and update README and CHANGELOG if `/auth/register` behaviour changes too. Per spec edge case "Logged-in user using the reset flow" and contracts/http-api.md (partial).
- [X] T054 [P] Add a `CapturedOutput` test to `test/PasswordResetFlowIT.java` (`@ExtendWith(OutputCaptureExtension.class)`, as `RegistrationFlowIT` does). Run a successful reset, a refused password, a wrong code, and an unknown contact with a distinctive new password, then assert that the captured output contains neither that password nor any delivered code. The recording sender does not log. Per SC-007 and FR-016 (partial).
- [X] T055 [P] Add a flow test to `test/PasswordResetFlowIT.java`: register accounts A and B, request a code for A, and confirm with B's email and A's code. Expect `400 Invalid or expired code`, B's password unchanged, and A's code still usable. Per spec edge case "Code submitted for a different email/phone than it was sent to" (partial).
- [X] T056 [P] Sync the design docs with the implementation:
  - in `specs/003-forgot-password/research.md` R4 and in `data-model.md`, record `core/valueobject/ContactRules` (shared email and phone format rules, plus the exactly-one-contact check);
  - in research R6, change "returns whether a code was issued" to "returns the account id (`OptionalLong`), empty when no code was issued".

  Per plan R4 and R6 (unrequested).

---

## Phase 9: Convergence

- [X] T057 CRITICAL: remove the unused `import lombok.Data;` from `main/api/request/ConfirmPasswordResetRequest.java` (the record uses no Lombok annotation), then run `./mvnw test -Dtest='!*IT,!MyAppTests'` to confirm it still compiles and passes. Per Constitution I (contradicts).
