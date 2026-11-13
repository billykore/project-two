# Implementation Plan: Forgot Password

**Branch**: `003-forgot-password` | **Date**: 2026-11-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-forgot-password/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

A user who forgot their password asks for a 6-digit one-time code with `POST /auth/password-reset/request`, giving
the email address or phone number on their account. The response is always the same `202`. They then send the code
and a new password to `POST /auth/password-reset/confirm`. If the code is right and the new password follows the
registration rules and is not one of the last 3 passwords, the password is replaced, the code is used up, every
session of the account ends, and the user logs in normally.

The work follows the existing hexagon, inside-out:

1. **Domain.**
   - The password rules move out of `RegisterUserCommand` into `PasswordRules`, so registration and reset share one
     definition (R4).
   - Two new commands validate the contact (exactly one of `email`/`phone`), the code, and the new password.
     Invalid input raises `InvalidPasswordResetException` with the same `errors[]` shape as registration
     (`FieldError` becomes name-based, R5).
   - A new entity `PasswordResetCode` owns the code lifecycle: issue, usable, failed attempt, used, invalidated.
   - A new entity `PasswordHistoryEntry` holds previous password hashes.
   - `User.changePassword` replaces the hash.
2. **Ports.**
   - New inbound port `PasswordResetUseCase`.
   - New outbound ports `PasswordResetCodeRepository`, `PasswordHistoryRepository`, `ResetCodeSender`, and
     `ResetCodeGenerator`.
   - `UserRepository` gains `findForPasswordReset(contact)` (row-locked) and `update`.
   - `SessionRepository` gains `revokeAllForUser`.
3. **Application.** All reset work runs under a lock on the account row (R6). This lock serializes the rate limits,
   the single open code, attempt counting, and single use.
   - `PasswordResetService.requestReset` is not transactional. It hashes a fresh code first (R11) and delegates
     issuing to `ResetCodeIssuer`, a `@Transactional` service.
   - The service sends the code only after that transaction commits (R6, R10).
   - A wrong code is committed despite the exception (`noRollbackFor`, R7).
   - The username and last-3 checks run only after the code is verified, and leave the code usable (FR-013).
4. **Persistence.** Migration `V12` adds `password_reset_codes` (with a partial unique index of one open code per
   account) and `password_history` (pruned to the 2 newest entries per account). Codes are hashed with
   HMAC-SHA256 under a dedicated key (`APP_RESET_CODE_SECRET`) through a new `ResetCodeHasher` port and its adapter
   `adapter/security/HmacResetCodeHasher` (R3).
   - Lock timeouts and index violations are translated into `PasswordResetBusyException`. Confirm maps it to `503`;
     request absorbs it into the generic `202` (R6).
5. **Delivery.** In the new `adapter/notification`:
   - `LoggingResetCodeSender` logs codes for developers. It is enabled only by `app.password-reset.log-codes=true`.
   - `UnavailableResetCodeSender` is the safe default. It sends nothing, and the failure is logged without the code.
     The response stays generic (R9, R10).
6. **HTTP.**
   - New `PasswordResetController` under `/auth/password-reset`, which is already public.
   - New `PasswordResetExceptionHandler`: `400 errors[]` for invalid input, and a separate `400 Invalid or expired
     code`.

No new runtime dependency and no event. Login, registration responses, and the JWT are unchanged.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server),
Lombok, Flyway. **None added.** Randomness uses the JDK's `SecureRandom`, and HMAC uses the JDK's `javax.crypto.Mac`.

**Configuration**: one new secret, `APP_RESET_CODE_SECRET` (Base64, at least 32 bytes), for hashing codes (R3). It
goes in `.env.example`, `src/test/resources/config/application.properties` (which CI also uses), and the README
configuration table.

**Storage**: PostgreSQL with Flyway. One new migration, `V12__create_password_reset_tables.sql`, with two new
tables. No change to existing tables or data.

**Testing**:
- JUnit 5, AssertJ, Mockito; plain unit tests for the commands, `PasswordRules`, entities, and the service (with
  a fixed `Clock` and a fixed code generator);
- `@PersistenceAdapterTest` (Testcontainers PostgreSQL) for the new repositories, the row lock, and session
  revocation;
- `@WebMvcTest` for `PasswordResetController`;
- a `@SpringBootTest` flow test with a recording `ResetCodeSender`, covering concurrent confirms with one code;
- ArchUnit unchanged.

Everything runs in `./mvnw test`.

**Target Platform**: Linux/macOS JVM service; tests need a Docker-compatible runtime.

**Project Type**: web service (single Spring Boot application, hexagonal layout)

**Performance Goals**: SC-001 (under 3 minutes end to end) and SC-002 (the code handed to the channel within 1 s for
95% of requests).
- **Request**: an HMAC hash (microseconds), one locked lookup, two count queries, and up to two writes. That is
  well under 1 s, which is what makes SC-002 reachable (R3). Quickstart section 5 measures it.
- **Confirm**: an HMAC check for the code, then BCrypt for the passwords at strength 14 (about 1 s each): up to 3
  compares against the last passwords and 1 hash for the new one. That is about 1–4 s while holding the account's
  row lock. The spec sets no latency target for confirm. The lock timeout (15 s by default, `app.password-reset.lock-timeout`, R6) bounds how long a concurrent reset
  of the same account waits.

**Constraints**:
- the request response must be identical for every well-formed request (FR-004, SC-005), including timing as far
  as practical (R11);
- codes and passwords are never logged or returned, except by the opt-in development sender (FR-016, R9);
- registration keeps its exact behaviour and messages after `PasswordRules` is extracted;
- the session mapping fix (`revokedAt` nullable) must not change login.

**Scale/Scope**:
- 34 new production classes and 14 changed, plus 1 migration;
- 12 new test classes and 5 changed;
- 2 endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Gates from `.specify/memory/constitution.md` (v2.0.2). Mark each ✅ pass, or ❌ with a row in Complexity Tracking.

- [x] ✅ **I. Clean, Readable Code**:
  - domain names (`PasswordResetCode`, `PasswordResetUseCase`, `ResetCodeSender`, `InvalidResetCodeException`);
  - constructor injection;
  - commands, `Contact`, `PasswordResetPolicy`, and DTOs are records;
  - specific domain exceptions are mapped to Problem Details;
  - `toString` masks codes and passwords;
  - the one new secret (`APP_RESET_CODE_SECRET`, R3) comes from the environment and is never committed; tests use a
    test-only value in `src/test/resources/config/application.properties`, like the JWT secret.

  `CodeDeliveryException` and, in `requestReset` only, `PasswordResetBusyException` are handled in the service on
  purpose. They are logged, and FR-004 decides the generic outcome (R6, R10). That is handling, not swallowing.
- [x] ✅ **II. Test Discipline**:
  - every rule has a Spring-free unit test: each command field, each `PasswordResetCode` transition, the limits,
    the history, and superseding;
  - deterministic: fixed `Clock`, fixed `ResetCodeGenerator`;
  - error paths covered: wrong, expired, used, superseded, and exhausted codes; refused passwords; delivery
    failure.

  The refactor of `RegisterUserCommand` keeps every `RegisterUserCommandTest` case and expected message. Only the
  type of `FieldError.field` in its assertions changes, from enum to name (R5).
- [x] ✅ **III. Integration & Contract Verification**:
  - V12, the partial unique index, `countIssuedSince`, `keepNewest`, and `revokeAllForUser` are tested on
    PostgreSQL;
  - the row lock has a two-transaction test;
  - concurrent confirms with one code have a conflict test (exactly one `204`);
  - both endpoints have `@WebMvcTest` cases for every documented status;
  - README API and configuration sections and the CHANGELOG are updated.

  No events.
- [x] ✅ **IV. Hexagonal Boundaries**:
  - new classes go in `core/{valueobject,entity,exception,port}`, `service`, `api/{controller,request,response,
    handler}`, `adapter/persistence`, `adapter/notification`, and `config`;
  - `adapter/notification` is a new adapter technology package (delivery of messages), not a new top-level
    package; AR-01/AR-02 already cover `adapter.(*)`;
  - `core` imports nothing outward.
- [x] ✅ **V. Maintainability & Simplicity**:
  - no dependency added;
  - one new migration;
  - tunables (TTL, attempts, resend interval, hourly cap) are in `PasswordResetProperties`, and the spec-fixed
    values (6 digits, 3 passwords) are constants (R12);
  - the duplicated password rules are consolidated rather than copied (R4);
  - no verify-step token, no real provider, no clean-up job (R1, R8, R9);
  - README and CHANGELOG are updated, and `.env.example` gains `APP_PASSWORD_RESET_LOG_CODES` and
    `APP_RESET_CODE_SECRET`;
  - `ResetCodeIssuer` is a second service class only because a lock failure must be handled outside the failed
    transaction (R6), not a speculative abstraction.
- [x] ✅ **VI. Ports Are Owned by the Application**:
  - the inbound port is `PasswordResetUseCase`;
  - the outbound ports are named for needs (`ResetCodeSender`, not `SmtpClient`; `ResetCodeGenerator`, not
    `SecureRandomWrapper`; `ResetCodeHasher`, not `HmacSha256`);
  - signatures use domain and JDK types only (`Contact`, `Instant`, `List<String>`);
  - `@throws` documents `InvalidResetCodeException`, `InvalidPasswordResetException`, `CodeDeliveryException`, and
    `PasswordResetBusyException`;
  - `@Lock` and `@Modifying` stay on the Spring Data interfaces inside `adapter/persistence`.
- [x] ✅ **VII. Adapters Translate, Never Decide**:
  - the controller builds a command, calls one use case, and maps the result (`202` / `204`);
  - the senders only deliver or fail, and the service decides what a failure means;
  - persistence adapters only query, lock, and update, and translate every infrastructure failure before it crosses
    a port: lock timeouts and the open-code index become `PasswordResetBusyException`, and other integrity
    violations become `IllegalStateException` (R6);
  - no adapter calls another.
- [x] ✅ **VIII. Domain Model at the Center**:
  - code lifecycle rules live in `PasswordResetCode`, input rules in the commands and `PasswordRules`, and the
    password change in `User`;
  - the service orchestrates the cross-aggregate checks (history, sessions);
  - `@Transactional` is only on the services (`ResetCodeIssuer.issue`, `PasswordResetService.resetPassword`);
  - `now` and the code come from `Clock` and `ResetCodeGenerator`, and are passed in as arguments.

**Result**: all eight gates pass. Complexity Tracking is empty.

**Post-design re-check** (after data-model.md and contracts/): still all ✅.
- `PasswordResetPolicy` is a `core` record built in `config` from `PasswordResetProperties`, as `FareCalculator`
  is from `FareProperties` (IV, VIII).
- `FieldError` holding a client-facing field name keeps it a plain JDK-typed value, matching the existing
  `RegistrationField.jsonName()` already in `core` (VI).
- `noRollbackFor` is a transaction-demarcation detail on the service, where Principle VIII puts transactions.
- The row lock is declared on `SpringDataUserRepository` (adapter) and described in domain terms on the port (VI,
  VII).

## Project Structure

### Documentation (this feature)

```text
specs/003-forgot-password/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R12
├── data-model.md        # Phase 1: value objects, entities, exceptions, migration V12, rule map
├── quickstart.md        # Phase 1: how to validate the feature end-to-end
├── contracts/
│   ├── http-api.md      # POST /auth/password-reset/request and /confirm
│   └── ports.md         # PasswordResetUseCase, new and extended outbound ports, service steps
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
src/main/java/org/kore/myapp/
├── core/
│   ├── entity/User.java                                  # CHANGED: changePassword(hash)
│   ├── entity/Session.java                               # CHANGED: revokedAt mapping nullable
│   ├── entity/PasswordResetCode.java                     # NEW
│   ├── entity/PasswordHistoryEntry.java                  # NEW
│   ├── valueobject/PasswordRules.java                    # NEW: shared password rules, HISTORY_SIZE
│   ├── valueobject/RegisterUserCommand.java              # CHANGED: uses PasswordRules, FieldError by name
│   ├── valueobject/FieldError.java                       # CHANGED: (String field, String message)
│   ├── valueobject/ContactChannel.java                   # NEW: EMAIL, PHONE
│   ├── valueobject/Contact.java                          # NEW
│   ├── valueobject/RequestPasswordResetCommand.java      # NEW
│   ├── valueobject/ResetPasswordCommand.java             # NEW
│   ├── valueobject/PasswordResetPolicy.java              # NEW
│   ├── exception/InvalidRegistrationException.java       # CHANGED: FieldError.field() is a name
│   ├── exception/InvalidPasswordResetException.java      # NEW
│   ├── exception/InvalidResetCodeException.java          # NEW
│   ├── exception/CodeDeliveryException.java              # NEW
│   ├── exception/PasswordResetBusyException.java         # NEW: lock timeout / open-code index
│   ├── port/PasswordResetUseCase.java                    # NEW: inbound
│   ├── port/PasswordResetCodeRepository.java             # NEW
│   ├── port/PasswordHistoryRepository.java               # NEW
│   ├── port/ResetCodeSender.java                         # NEW
│   ├── port/ResetCodeGenerator.java                      # NEW
│   ├── port/ResetCodeHasher.java                         # NEW: keyed code hash (R3)
│   ├── port/UserRepository.java                          # CHANGED: findForPasswordReset, update
│   └── port/SessionRepository.java                       # CHANGED: revokeAllForUser
├── service/PasswordResetService.java                     # NEW: requestReset not transactional; sends after commit
├── service/ResetCodeIssuer.java                          # NEW: @Transactional issuing step (R6)
├── api/
│   ├── controller/PasswordResetController.java           # NEW: /auth/password-reset/{request,confirm}
│   ├── request/RequestPasswordResetRequest.java          # NEW
│   ├── request/ConfirmPasswordResetRequest.java          # NEW: masked toString
│   ├── response/PasswordResetRequestedResponse.java      # NEW: generic message
│   ├── handler/PasswordResetExceptionHandler.java        # NEW: 400 errors[], 400 invalid code, 503 busy
│   └── handler/RegistrationExceptionHandler.java         # CHANGED: FieldError.field() is a name
├── adapter/
│   ├── persistence/JpaUserRepository.java                # CHANGED
│   ├── persistence/SpringDataUserRepository.java         # CHANGED: findLockedByEmail/Phone (@Lock)
│   ├── persistence/PasswordResetLockProperties.java      # NEW: app.password-reset.lock-timeout
│   ├── persistence/JpaSessionRepository.java             # CHANGED
│   ├── persistence/SpringDataSessionRepository.java      # CHANGED: @Modifying revoke query
│   ├── persistence/JpaPasswordResetCodeRepository.java   # NEW
│   ├── persistence/SpringDataPasswordResetCodeRepository.java  # NEW
│   ├── persistence/JpaPasswordHistoryRepository.java     # NEW
│   ├── persistence/SpringDataPasswordHistoryRepository.java    # NEW
│   ├── security/HmacResetCodeHasher.java                 # NEW: HMAC-SHA256
│   ├── security/ResetCodeHasherProperties.java           # NEW: app.password-reset.code-secret
│   ├── notification/LoggingResetCodeSender.java          # NEW: log-codes=true
│   └── notification/UnavailableResetCodeSender.java      # NEW: default
└── config/
    ├── PasswordResetProperties.java                      # NEW: app.password-reset.*
    ├── ApplicationConfig.java                            # CHANGED: policy + ResetCodeGenerator beans
    └── SecurityConfig.java                               # CHANGED: enable ResetCodeHasherProperties
src/main/resources/
├── application.properties                                # CHANGED: app.password-reset.* defaults
└── db/migration/V12__create_password_reset_tables.sql    # NEW

src/test/java/org/kore/myapp/
├── core/valueobject/PasswordRulesTest.java               # NEW
├── core/valueobject/RequestPasswordResetCommandTest.java # NEW
├── core/valueobject/ResetPasswordCommandTest.java        # NEW
├── core/entity/PasswordResetCodeTest.java                # NEW
├── core/valueobject/RegisterUserCommandTest.java         # CHANGED: field assertions by name (R5)
├── core/entity/UserTest.java                             # CHANGED: changePassword
├── core/exception/RegistrationExceptionsTest.java        # CHANGED: field assertions by name (R5)
├── service/PasswordResetServiceTest.java                 # NEW
├── adapter/persistence/JpaPasswordResetCodeRepositoryIT.java  # NEW
├── adapter/persistence/JpaPasswordHistoryRepositoryIT.java    # NEW
├── adapter/persistence/JpaUserRepositoryIT.java          # CHANGED: findForPasswordReset, lock
├── adapter/persistence/JpaUserAndSessionRepositoryIT.java     # CHANGED: revokeAllForUser
├── adapter/notification/LoggingResetCodeSenderTest.java  # NEW
├── adapter/notification/UnavailableResetCodeSenderTest.java   # NEW
├── adapter/security/HmacResetCodeHasherTest.java          # NEW
├── api/controller/PasswordResetControllerTest.java       # NEW
└── PasswordResetFlowIT.java                              # NEW: end to end, concurrency, attempt limit

src/test/resources/config/application.properties          # CHANGED: test-only APP_RESET_CODE_SECRET
README.md, CHANGELOG.md, .env.example                     # CHANGED
```

**Structure Decision**: the existing single-application hexagonal layout (constitution Principle IV). Password reset
is part of authentication, so it lives under the already-public `/auth/**` path. It gets its own controller, use
case, and service, so `AuthController`, `AuthService`, and `RegistrationService` stay focused. Delivery is a new
outbound technology, so it gets its own `adapter/notification` package; a real email or SMS adapter will be added
there later.

## Revisions after `/speckit-analyze` (2026-11-13)

- **A1 (SC-002 unreachable)**: codes are hashed with HMAC-SHA256 through the new `ResetCodeHasher` port instead of
  BCrypt (R3). This adds one secret, `APP_RESET_CODE_SECRET`.
- **C1 (untranslated infrastructure exceptions)**: lock timeouts and the open-code index are translated into
  `PasswordResetBusyException`, and other integrity violations into `IllegalStateException` (R6).
  - `requestReset` moved its transaction into `ResetCodeIssuer`, so the exception can be handled after the failed
    transaction has ended.
  - As a side effect, codes are now sent after commit.
- **Second analysis (H1, H2, I2, A2)**:
  - H1: the lock timeout is set with `SET LOCAL lock_timeout` (default 15 s, configurable) instead of a JPA hint.
  - H2: the concurrent-confirm test asserts that the password changed exactly once.
  - I2: `logCodes` was removed from `PasswordResetProperties`.
  - A2: FR-016 names the development-sender exception.
- **I1, C2, D1**: fixed in tasks.md (sender bean in the flow test, wording of T015, duplicate property).

## Spec follow-up

None required. Two refinements are consistent with the spec and recorded here for reviewers:

- **Spec edge case "Delivery to email or phone fails ... recorded for operators without the code"**: this holds for
  the default `UnavailableResetCodeSender` too, so a deployment without `log-codes` stays safe (R9).
- **SC-005**: SC-005 covers content and status only. The plan also equalizes the dominant timing cost (R11). The
  spec does not need to change.

## Complexity Tracking

No violations to justify.
