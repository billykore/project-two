# Implementation Plan: User Registration

**Branch**: `002-user-registration` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-user-registration/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Add `POST /auth/register`. A visitor provides a username, password, name, email address, and phone number. The
system creates an ordinary customer account and responds `201` with the account (never the password). The user
then logs in through the unchanged `POST /auth/login`.

The work follows the existing hexagon, inside-out:

1. **Domain.** `RegisterUserCommand` (in `core/valueobject`) owns every input rule. It normalizes (trims the name,
   trims and lowercases the email) and collects *all* violations into one `InvalidRegistrationException`, so a
   request with several bad fields gets every one of them back (FR-014). `User.register(...)` builds the new
   entity. Uniqueness conflicts are a separate `RegistrationConflictException` carrying the set of fields in use.
2. **Ports.** A new inbound `RegistrationUseCase`. `UserRepository` gains `existsByUsername`, `existsByEmail`,
   `existsByPhone`, and `add`.
3. **Application.** `RegistrationService` (`@Transactional`) checks the three uniqueness rules first, so a normal
   conflict names every taken field. It then hashes the password with the existing `PasswordHasher` and adds the
   user.
4. **Persistence.** Migration `V11` adds nullable `email` and `phone` columns with unique indexes. Existing
   accounts have neither, but every new registration sets both. `JpaUserRepository.add` flushes and translates a
   unique-index violation into `RegistrationConflictException` for the field concerned. That makes two
   simultaneous registrations safe (FR-004, FR-020).
5. **HTTP.** `AuthController.register` maps the request to the command, calls the use case, and returns
   `RegisterResponse`. A new `RegistrationExceptionHandler` maps the two exceptions to Problem Details: `400` with
   an `errors` list, and `409` with a `conflicts` list. A missing or malformed JSON body now gets a Problem Details
   `400` too.

No new runtime dependency and no new event. Login, the JWT, and sessions are untouched.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server),
Lombok, Flyway. **None added.**

**Storage**: PostgreSQL with Flyway. One new migration, `V11__add_email_and_phone_to_users.sql`, which adds two
nullable columns and two unique indexes. No data migration.

**Testing**: JUnit 5, AssertJ, Mockito; `@WebMvcTest` for `AuthController`; `@PersistenceAdapterTest`
(Testcontainers PostgreSQL 16) for `JpaUserRepository`, including concurrent-insert races; a `@SpringBootTest` flow
test for register → login; ArchUnit unchanged. Everything runs in `./mvnw test`.

**Target Platform**: Linux/macOS JVM service; tests need a Docker-compatible runtime.

**Project Type**: web service (single Spring Boot application, hexagonal layout)

**Performance Goals**: SC-005, under 2 s for 95% of registrations. Most of the time goes to BCrypt at strength 14
(the existing `PasswordEncoder`, about 1 s per hash on a laptop). The three `exists` lookups use unique indexes.
No row lock is held while hashing. As with login today, the transaction does hold a pool connection for that
second (research R6).

**Constraints**:
- login, `LoginRequest`/`LoginResponse`, and every other contract stay identical (FR-022);
- passwords are never logged or returned (FR-009);
- accounts that existed before this feature keep working without an email or phone;
- BCrypt accepts at most 72 **bytes**, so the password limit is 72 characters and 72 UTF-8 bytes (FR-005,
  research R3).

**Scale/Scope**: 10 new production classes, 6 changed, 1 migration; 6 new test classes and 2 changed. One
endpoint.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Gates from `.specify/memory/constitution.md` (v2.0.1). Mark each ✅ pass, or ❌ with a row in Complexity Tracking.

- [x] ✅ **I. Clean, Readable Code**: domain names (`RegisterUserCommand`, `RegistrationUseCase`,
  `RegistrationConflictException`); constructor injection; command, DTOs, and field-error value are records; two
  specific domain exceptions mapped to Problem Details; no secrets.
- [x] ✅ **II. Test Discipline**: every rule in the command, `User.register`, and the service has plain unit tests
  without Spring; error paths (each invalid field, several at once, each conflict, several at once) are tested;
  clock-free and deterministic.
- [x] ✅ **III. Integration & Contract Verification**: V11 and the new queries are tested against PostgreSQL; the
  unique indexes have concurrent-conflict tests for username, email, and phone; the endpoint has a `@WebMvcTest`
  for 201/400/409 and malformed bodies; the README API section and CHANGELOG are updated.
- [x] ✅ **IV. Hexagonal Boundaries**: classes go in `core/valueobject`, `core/entity`, `core/exception`,
  `core/port`, `service`, `api/{controller,request,response,handler}`, `adapter/persistence`, and
  `resources/db/migration`. No new package, and ArchUnit AR-01..AR-09 stay green.
- [x] ✅ **V. Maintainability & Simplicity**: no new dependency, abstraction, or tunable (the rule limits come
  from the spec, not from configuration, research R8); a new migration instead of editing V1; README "Create a
  user" step, API table, and feature-status list plus CHANGELOG updated.
- [x] ✅ **VI. Ports Are Owned by the Application**: the new inbound port is `RegistrationUseCase`; `UserRepository`
  grows only by the four operations the service calls; signatures use `String`, `boolean`, `User`, and
  `RegisterUserCommand`; `@throws` documents both new exceptions.
- [x] ✅ **VII. Adapters Translate, Never Decide**: the controller maps request → command → one use case →
  response. `JpaUserRepository` translates `DataIntegrityViolationException` on the three unique constraints into
  `RegistrationConflictException`. The service catches no framework exception.
- [x] ✅ **VIII. Domain Model at the Center**: input rules live in the command constructor; account creation is
  `User.register(...)`; `@Transactional` sits on `RegistrationService`. `created_at` keeps its database default,
  since no rule or response reads it (research R7); no clock or id is needed.

**Result**: all eight gates pass. Complexity Tracking is empty.

**Post-design re-check** (after data-model.md and contracts/): still all ✅.
- The inbound adapter builds `RegisterUserCommand`, as `TripController` builds `CreateTripCommand`; the
  constructor's `InvalidRegistrationException` is a domain exception mapped at the edge.
- `JpaUserRepository` only translates constraint names to `RegistrationConflictException` (VII).
- `User.register` depends only on `core/valueobject` (IV).
- No port mentions a framework type (VI).

## Project Structure

### Documentation (this feature)

```text
specs/002-user-registration/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R10
├── data-model.md        # Phase 1: User changes, command, exceptions, migration V11
├── quickstart.md        # Phase 1: how to validate the feature end-to-end
├── contracts/
│   ├── http-api.md      # POST /auth/register request, responses, Problem Details
│   └── ports.md         # RegistrationUseCase and UserRepository additions
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
src/main/java/org/kore/myapp/
├── core/
│   ├── entity/User.java                              # CHANGED: email, phone; static register(...)
│   ├── valueobject/RegisterUserCommand.java          # NEW: input rules + normalization
│   ├── valueobject/RegistrationField.java            # NEW: enum USERNAME, PASSWORD, NAME, EMAIL, PHONE
│   ├── valueobject/FieldError.java                   # NEW: record (field, message)
│   ├── exception/InvalidRegistrationException.java   # NEW: List<FieldError>
│   ├── exception/RegistrationConflictException.java  # NEW: Set<RegistrationField>
│   ├── port/RegistrationUseCase.java                 # NEW: inbound port
│   └── port/UserRepository.java                      # CHANGED: existsBy{Username,Email,Phone}, add
├── service/RegistrationService.java                  # NEW: implements RegistrationUseCase
├── api/
│   ├── controller/AuthController.java                # CHANGED: POST /auth/register
│   ├── request/RegisterRequest.java                  # NEW
│   ├── response/RegisterResponse.java                # NEW
│   ├── handler/RegistrationExceptionHandler.java     # NEW: 400 errors[], 409 conflicts[]
│   └── handler/GlobalExceptionHandler.java           # CHANGED: Problem Details for unreadable bodies
└── adapter/persistence/
    ├── JpaUserRepository.java                        # CHANGED: exists*, add + constraint translation
    └── SpringDataUserRepository.java                 # CHANGED: existsBy* derived queries
src/main/resources/db/migration/
└── V11__add_email_and_phone_to_users.sql             # NEW

src/test/java/org/kore/myapp/
├── core/valueobject/RegisterUserCommandTest.java     # NEW
├── core/entity/UserTest.java                         # NEW
├── service/RegistrationServiceTest.java              # NEW
├── adapter/persistence/JpaUserRepositoryIT.java      # NEW (register-related; existing user/session IT unchanged)
├── adapter/persistence/PersistenceFixtures.java      # CHANGED: insertUser with email/phone
├── api/controller/AuthControllerTest.java            # CHANGED: register cases
└── RegistrationFlowIT.java                           # NEW: register → login over HTTP (SC-001, FR-011)

README.md, CHANGELOG.md                               # CHANGED
```

**Structure Decision**: the existing single-application hexagonal layout (constitution Principle IV). Registration
is part of authentication, so it is served under the existing `AuthController` and the already-public `/auth/**`
path. It gets its own use case and service so that `AuthService` (login) stays unchanged.

## Spec follow-up

Research R3 found that BCrypt's 72-byte limit is stricter than the spec's "72 characters" for any non-ASCII
password (for example, `é` is 2 bytes). The plan enforces both: at most 72 characters **and** at most 72 bytes in
UTF-8, with a message that says so. **Done**: FR-005, the edge cases, and the Assumptions in spec.md were amended
on 2026-11-12 to state the 72-byte limit.

## Complexity Tracking

No violations to justify.
