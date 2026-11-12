---

description: "Task list for User Registration"
---

# Tasks: User Registration

**Input**: Design documents from `/specs/002-user-registration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/http-api.md, contracts/ports.md

**Tests**: Test tasks are MANDATORY (constitution Principles II and III). Each story lists its tests first; write
them and see them fail before the implementation tasks.

**Organization**: Tasks are grouped by user story. Tests live under `src/test/java/org/kore/myapp/`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 = create an account and log in (P1), US2 = uniqueness (P2), US3 = clear feedback on invalid
  details (P3)

## Path Conventions

- Main code: `src/main/java/org/kore/myapp/...`; tests: `src/test/java/org/kore/myapp/...`; migrations:
  `src/main/resources/db/migration/`
- Hexagonal rule (constitution IV): `core` imports no other application package; `api` and `adapter` reach the
  application only through `core/port`.

---

## Phase 1: Setup

- [X] T001 Confirm a green baseline on branch `002-user-registration` by running `./mvnw test` (needs Docker); fix nothing, just record that all tests, including `ArchitectureTest`, pass before changes.

---

## Phase 2: Foundational (blocks all user stories)

**Purpose**: the schema, shared domain types, and port signatures every story needs, so the code compiles at every step.

- [X] T002 Create migration `src/main/resources/db/migration/V11__add_email_and_phone_to_users.sql` exactly as in data-model.md: nullable `email VARCHAR(254)` and `phone VARCHAR(16)`, `CHECK (email = lower(email))` named `chk_users_email_lowercase`, unique indexes `uq_users_email` and `uq_users_phone`. Do not edit V1–V10.
- [X] T003 [P] Create enum `RegistrationField` (`USERNAME, PASSWORD, NAME, EMAIL, PHONE`, each with `jsonName()`) in `src/main/java/org/kore/myapp/core/valueobject/RegistrationField.java`.
- [X] T004 [P] Create `record FieldError(RegistrationField field, String message)` in `src/main/java/org/kore/myapp/core/valueobject/FieldError.java`.
- [X] T005 [P] Unit test the two registration exceptions in `src/test/java/org/kore/myapp/core/exception/RegistrationExceptionsTest.java`: `InvalidRegistrationException` message is `"<jsonName>: <message>"` joined by `"; "` and its list is unmodifiable and in field order; `RegistrationConflictException` messages are "username already taken" / "email already registered" / "phone number already registered", joined with `"; "` in username, email, phone order, and its set is non-empty and unmodifiable (an empty set is rejected).
- [X] T006 [P] Create `RegistrationConflictException` (`Set<RegistrationField> getFields()`, EnumSet copy) in `src/main/java/org/kore/myapp/core/exception/RegistrationConflictException.java` per data-model.md.
- [X] T007 Create `InvalidRegistrationException` (`List<FieldError> getErrors()`) in `src/main/java/org/kore/myapp/core/exception/InvalidRegistrationException.java` per data-model.md (depends on T004).
- [X] T008 [P] Add `email` and `phone` fields to `User` in `src/main/java/org/kore/myapp/core/entity/User.java` (`@Column(length = 254)` and `@Column(length = 16)`, both nullable, unique); update the `createdAt` Javadoc, which no longer holds because the application now inserts users.
- [X] T009 [P] Add an `insertUser(String usernamePrefix, String email, String phone)` overload that also sets those columns, keeping the existing `insertUser(String)` working, in `src/test/java/org/kore/myapp/adapter/persistence/PersistenceFixtures.java`.
- [X] T010 [P] Create the bare `record RegisterUserCommand(String username, String password, String name, String email, String phone)` (no constructor body yet) in `src/main/java/org/kore/myapp/core/valueobject/RegisterUserCommand.java`, then the inbound port `RegistrationUseCase` (`User register(RegisterUserCommand command)`, `@throws RegistrationConflictException`) in `src/main/java/org/kore/myapp/core/port/RegistrationUseCase.java` per contracts/ports.md.
- [X] T011 Extend `UserRepository` with `existsByUsername`, `existsByEmail`, `existsByPhone`, and `User add(User)` (with the `@throws RegistrationConflictException` Javadoc) in `src/main/java/org/kore/myapp/core/port/UserRepository.java`.
- [X] T012 [P] Add derived queries `existsByUsername`, `existsByEmail`, `existsByPhone` to `src/main/java/org/kore/myapp/adapter/persistence/SpringDataUserRepository.java`.
- [X] T013 Implement the four new methods in `src/main/java/org/kore/myapp/adapter/persistence/JpaUserRepository.java`; `add` is a plain `saveAndFlush` for now (constraint translation comes in T030). Depends on T011, T012.

**Checkpoint**: project compiles, existing tests still pass, V11 applies to a fresh database.

---

## Phase 3: User Story 1 - Create an account and log in (Priority: P1) 🎯 MVP

**Goal**: a visitor registers with five valid fields, gets `201` with the account (no password), and can log in.

**Independent Test**: register `budi` through `POST /auth/register`, see the response, then `POST /auth/login` with the same credentials and receive a token.

### Tests for User Story 1 (MANDATORY) ⚠️

- [X] T014 [P] [US1] Unit test normalization in `src/test/java/org/kore/myapp/core/valueobject/RegisterUserCommandTest.java`: a valid command keeps username/password/phone as given, strips the name (`"  Budi Santoso "` → `"Budi Santoso"`), strips and lowercases the email (`" Budi@Example.com "` → `"budi@example.com"`), keeps spaces inside the password, and `toString()` shows `password=***` and never the password.
- [X] T015 [P] [US1] Unit test `User.register` in `src/test/java/org/kore/myapp/core/entity/UserTest.java`: copies username, name, email, phone from the command, stores the given hash (not the command's raw password), leaves `id` and `createdAt` null, and throws `IllegalArgumentException` for a blank hash.
- [X] T016 [P] [US1] Unit test the happy path of `RegistrationService` with mocked `UserRepository` and `PasswordHasher` in `src/test/java/org/kore/myapp/service/RegistrationServiceTest.java`: hashes the raw password once, saves a user holding the hash, returns the saved user, and never passes the raw password to the repository.
- [X] T017 [P] [US1] Integration test in `src/test/java/org/kore/myapp/adapter/persistence/JpaUserRepositoryIT.java` (`@PersistenceAdapterTest`): `add` persists username, name, hash, email, phone, assigns an id, and the reloaded row has a database-set `createdAt`; a legacy user inserted without email/phone (T009's old overload) coexists with a second one (two `NULL`s do not collide).
- [X] T018 [P] [US1] Web-layer test in `src/test/java/org/kore/myapp/api/controller/AuthControllerTest.java` (add `@MockitoBean RegistrationUseCase`): `POST /auth/register` with no token returns `201`, JSON body has exactly `id, username, name, email, phone` and no `password` or token; extra property `"role":"DRIVER"` is ignored; the use case receives a command with the normalized values.
- [X] T019 [P] [US1] End-to-end test `src/test/java/org/kore/myapp/RegistrationFlowIT.java` (`@SpringBootTest` over HTTP with the same Testcontainers setup as `MyAppTests`). The database keeps committed rows across test methods and classes, so add a helper `uniqueApplicant(String prefix)` that returns a fresh, valid username (lowercase prefix plus a short random suffix, at most 50 characters), email (`<username>@example.com`), and phone (`+62` followed by 10 random digits). Every test method calls it for each account it creates; no test uses a fixed username, email, or phone, and no test depends on another test's data or on run order. Then: register an applicant, log in with the same credentials and get `200` with an access token; log in with a wrong password and get `401`; log in with the applicant's email, and then their phone, in place of the username (correct password) and get `401` each time (FR-022); the registered user has no `driver_profiles` row; the stored password column is a BCrypt hash.

### Implementation for User Story 1

- [X] T020 [US1] Add normalization (strip name, strip and lowercase email) in a compact constructor, and a masked `toString`, to the `RegisterUserCommand` record from T010 in `src/main/java/org/kore/myapp/core/valueobject/RegisterUserCommand.java`. Validation rules are added in T036; see the merge rule under Implementation Strategy.
- [X] T021 [US1] Add `static User register(RegisterUserCommand command, String passwordHash)` to `src/main/java/org/kore/myapp/core/entity/User.java` (depends on T020).
- [X] T022 [US1] Create `RegistrationService` (`@Service`, `@Transactional`, implements `RegistrationUseCase`; hash with `PasswordHasher`, build with `User.register`, save with `userRepository.add`; log `info` "user registered, id={}, username={}" only) in `src/main/java/org/kore/myapp/service/RegistrationService.java` (depends on T021).
- [X] T023 [P] [US1] Create `RegisterRequest` record (five `String` components, no validation annotations, see research R1) in `src/main/java/org/kore/myapp/api/request/RegisterRequest.java`.
- [X] T024 [P] [US1] Create `RegisterResponse` record (`Long id, String username, String name, String email, String phone`, `static from(User)`) in `src/main/java/org/kore/myapp/api/response/RegisterResponse.java`.
- [X] T025 [US1] Add `POST /auth/register` (`@ResponseStatus(CREATED)`, builds `RegisterUserCommand`, calls `RegistrationUseCase`, returns `RegisterResponse`) to `src/main/java/org/kore/myapp/api/controller/AuthController.java` (depends on T022–T024). `/auth/**` is already public in `SecurityConfig`.

**Checkpoint**: US1 works alone: valid registration, then login. T014–T019 pass.

---

## Phase 4: User Story 2 - Usernames, email addresses, and phone numbers stay unique (Priority: P2)

**Goal**: a taken username, email (case-insensitive), or phone is refused with `409` naming each taken field, nothing is changed, and concurrent duplicates yield exactly one account.

**Independent Test**: register `budi`, then re-register reusing the username, the email (different case), and the phone in turn; each gets `409` naming the field and `budi` can still log in.

### Tests for User Story 2 (MANDATORY) ⚠️

- [X] T026 [P] [US2] Extend `src/test/java/org/kore/myapp/service/RegistrationServiceTest.java`: each of username, email, phone taken → `RegistrationConflictException` naming exactly that field; all three taken → all three named; when any is taken `add` and `hash` are never called.
- [X] T027 [P] [US2] Extend `src/test/java/org/kore/myapp/adapter/persistence/JpaUserRepositoryIT.java`: `existsBy*` return true/false correctly; `add` of a duplicate username, email, or phone throws `RegistrationConflictException` naming that field (this asserts the constraint names `users_username_key`, `uq_users_email`, `uq_users_phone`); two threads adding the same username, then the same email, then the same phone (follow `JpaTripRepositoryIT.concurrentAddsForOneCustomerLetExactlyOneSucceed`: the test method is `@Transactional(propagation = NOT_SUPPORTED)`, each thread commits through its own `TransactionTemplate`, and values are unique to the test): exactly one succeeds and the other gets `RegistrationConflictException`; an unrelated integrity violation (for example mixed-case email breaking `chk_users_email_lowercase`) propagates as `DataIntegrityViolationException`.
- [X] T028 [P] [US2] Extend `src/test/java/org/kore/myapp/api/controller/AuthControllerTest.java`: the use case throwing `RegistrationConflictException` for `{USERNAME, EMAIL}` gives `409`, `application/problem+json`, `title` "Registration conflict", `detail` "username already taken; email already registered", `conflicts` `["username","email"]`.
- [X] T029 [P] [US2] Extend `src/test/java/org/kore/myapp/RegistrationFlowIT.java`, using `uniqueApplicant` (T019) for every account: register applicant A; re-registering with A's username, with A's email in upper case, and with A's phone (each time with a fresh applicant's other fields) returns `409` with the right `conflicts`; after those attempts A still logs in with the original password and A's name, email, and phone are unchanged; two concurrent registrations of one fresh applicant over HTTP give one `201` and one `409`.

### Implementation for User Story 2

- [X] T030 [US2] Change `JpaUserRepository.add` in `src/main/java/org/kore/myapp/adapter/persistence/JpaUserRepository.java` to catch `DataIntegrityViolationException`, walk the cause chain like `JpaTripRepository.violates`, map the constraint name to `RegistrationField` (constants for the three names, research R4), throw `RegistrationConflictException`, and rethrow anything else.
- [X] T031 [US2] In `RegistrationService` (`src/main/java/org/kore/myapp/service/RegistrationService.java`), collect the taken fields with the three `existsBy*` calls before hashing; if any, log `info` "registration conflict, fields={}" (fields only, no values) and throw `RegistrationConflictException`.
- [X] T032 [US2] Create `RegistrationExceptionHandler` (`@RestControllerAdvice`) in `src/main/java/org/kore/myapp/api/handler/RegistrationExceptionHandler.java` mapping `RegistrationConflictException` to `409` Problem Details per contracts/http-api.md (title, `detail`, `conflicts` array of JSON names in username, email, phone order).

**Checkpoint**: US1 and US2 both work; T026–T029 pass.

---

## Phase 5: User Story 3 - Clear feedback on invalid details (Priority: P3)

**Goal**: invalid input is rejected with `400`, listing every invalid field with a reason, and nothing is created.

**Independent Test**: submit requests that break each rule one at a time and several at once; each gets `400` with the offending fields and no account exists afterwards.

### Tests for User Story 3 (MANDATORY) ⚠️

- [X] T033 [P] [US3] Extend `src/test/java/org/kore/myapp/core/valueobject/RegisterUserCommandTest.java` with every rule and boundary from data-model.md: username (null, 2/3 and 50/51 chars, uppercase, space, `!`, leading space); password (null, 7/8 characters, 72/73 characters, 40 × `é` = 80 bytes rejected, 72 ASCII accepted, `abcdefgh` (no digits, capitals, or symbols) accepted (FR-007), equals username, spaces kept); name (null, blank, 100/101 chars after strip, non-ASCII accepted); email (null, blank, `budi@`, `budi.example.com`, `a@b`, two `@`, 254/255 chars, local part over 64); phone (null, `081234567890`, `+62 812-3456-7890`, `+1234567` (7 digits), 8 and 15 digits accepted, 16 rejected). Plus: several invalid fields produce one `InvalidRegistrationException` listing all of them once, in field order, and a valid command throws nothing.
- [X] T034 [P] [US3] Extend `src/test/java/org/kore/myapp/api/controller/AuthControllerTest.java`: invalid input gives `400`, `application/problem+json`, `title` "Invalid registration", `detail` "password: must be at least 8 characters; phone: …", and an `errors` array of `{field, message}` objects; a missing property is reported as "is required"; an empty body and malformed JSON give `400` "Malformed request" with the fixed detail, and the response never echoes the request body; the use case is never called in any of these cases.
- [X] T035 [P] [US3] Extend `src/test/java/org/kore/myapp/RegistrationFlowIT.java`, starting each case from a fresh `uniqueApplicant` (T019) and breaking one or more fields: a 7-character password, an invalid username, and an all-invalid request over HTTP each return `400`, and afterwards no account exists with that applicant's username, email, or phone. Check these specific values, not the total row count, which other tests change.

### Implementation for User Story 3

- [X] T036 [US3] Add all validation to the `RegisterUserCommand` constructor in `src/main/java/org/kore/myapp/core/valueobject/RegisterUserCommand.java`: `static final` limits (research R8), email and phone patterns (research R5, FR-018), password 8–72 code points and 72 UTF-8 bytes (research R3), password ≠ username; collect all violations (one message per field) and throw `InvalidRegistrationException` once. Normalization from T020 stays.
- [X] T037 [US3] Map `InvalidRegistrationException` to `400` Problem Details (title "Invalid registration", `detail` as `field: message` joined by `"; "`, `errors` array) in `src/main/java/org/kore/myapp/api/handler/RegistrationExceptionHandler.java`.
- [X] T038 [US3] Add an `HttpMessageNotReadableException` handler returning `400` "Malformed request" with the fixed detail "Request body is missing or is not valid JSON" to `src/main/java/org/kore/myapp/api/handler/GlobalExceptionHandler.java` (research R10).

**Checkpoint**: all three stories work; T033–T035 pass.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T039 [P] Update `README.md`: replace step 5 "Create a user to log in with" with the registration `curl` flow (keep a short note on direct SQL for creating drivers), add `POST /auth/register` with request, `201`, `400`, `409` and the password/phone rules to the API section, state in the API section's general error notes that any endpoint answers a missing or non-JSON body with `400` "Malformed request" Problem Details, add the `email` and `phone` columns to the `users` table row, and tick the registration part of the feature-status checklist (driver onboarding stays open).
- [X] T040 [P] Update `CHANGELOG.md` under **Unreleased**: Added `POST /auth/register`, migration V11 (email and phone on `users`); Changed Problem Details `400` for unreadable request bodies on all endpoints.
- [X] T041 [P] Add a log-capture assertion (Spring Boot `OutputCaptureExtension`) to `src/test/java/org/kore/myapp/RegistrationFlowIT.java` proving that after a success, a conflict, and an invalid request, the captured output contains none of the raw password, the email, or the phone (SC-004).
- [X] T042 Run `./mvnw test` and confirm everything passes, including `ArchitectureTest` (AR-01..AR-09) with no new compiler warnings.
- [X] T043 Run through `specs/002-user-registration/quickstart.md` against a locally running app and confirm every expected result, including the SQL check that `is_bcrypt` is true and the section 4 timing check for SC-005 (at least 19 of 20 sequential registrations under 2 s; record the slowest time in the pull request).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → **User stories** → **Polish**.
- Foundational blocks all stories: it holds the migration, shared types, and port signatures.

### User Story Dependencies

- **US1 (P1)**: starts after Foundational; no dependency on other stories.
- **US2 (P2)**: starts after Foundational and is testable alone, but it edits `JpaUserRepository.add` (T013), `RegistrationService` (T022), and the test files created in US1, so do it after US1 when working alone.
- **US3 (P3)**: starts after Foundational, but it edits `RegisterUserCommand` (T020) and shares `RegistrationExceptionHandler` with US2 (T032 creates it, T037 extends it), so do it after US1 and US2 when working alone.

### Within Each User Story

- Tests first and failing, then: command/entity → service → adapters → controller and handlers (inside-out, constitution workflow).

### Parallel Opportunities

- Foundational: T003, T004, T005, T006, T008, T009, T010, T012 are different files; T007 follows T004, T013 follows T011 and T012.
- US1 tests T014–T019 are six different files; T023 and T024 can run together.
- US2 tests T026–T029 and US3 tests T033–T035 each touch different files within their phase (they extend files US1 created, so they wait for US1).
- Polish: T039, T040, T041 in parallel.

### Parallel Example: User Story 1

```text
# Tests together:
T014 RegisterUserCommandTest   T015 UserTest   T016 RegistrationServiceTest
T017 JpaUserRepositoryIT       T018 AuthControllerTest   T019 RegistrationFlowIT

# DTOs together:
T023 RegisterRequest   T024 RegisterResponse
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 (T001–T013).
2. Phase 3 (T014–T025). Stop and validate: register, then log in.
3. Not safe to ship yet: without US2 a duplicate gives a raw `500`, and without US3 invalid input is not validated. Ship at least US1 + US2 + US3 together, or keep the endpoint unreleased until then.
4. **Merge rule (constitution VI)**: between T020 and T036, `RegisterUserCommand` does not yet validate its own invariants. Work in that window may be committed on this branch, but MUST NOT be merged to `main` until T036 is done and its tests pass.

### Incremental Delivery

1. Foundation → US1 (working registration) → US2 (uniqueness and `409`) → US3 (validation and `400`) → Polish.
2. Commit after each task or small group; run `./mvnw test` at every checkpoint.

---

## Notes

- Test tasks: T005, T014–T019, T026–T029, T033–T035, and T041.
- Tests that commit to the shared PostgreSQL container (`RegistrationFlowIT`, and the concurrent cases in `JpaUserRepositoryIT`) MUST use values unique to each test method, never fixed usernames, emails, or phones (constitution II: no shared mutable state, no reliance on test order).
- Never log or return the raw password, email, or phone outside the response (FR-009, SC-004).
- Do not edit merged migrations; V11 is the only schema change.
- No new package, dependency, or port beyond those in plan.md.
