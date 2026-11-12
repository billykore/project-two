# Research: User Registration

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-12

The Technical Context had no NEEDS CLARIFICATION items: the stack, storage, and test setup all come from feature
001. The questions below are the design decisions this feature needed, checked against the current code.

## R1. Where the input rules live

**Decision**: every rule from FR-002, FR-005, FR-006, FR-008, FR-016, and FR-018 lives in the
`RegisterUserCommand` constructor in `core/valueobject`. The constructor checks every field, collects *all*
violations, and throws one `InvalidRegistrationException(List<FieldError>)`. `RegisterRequest` has no Bean
Validation annotations; a missing field arrives as `null` and the command reports it as "is required".

**Rationale**:
- FR-014 asks for every invalid field at once. If Bean Validation checked presence and the command checked format,
  a request with a missing name *and* a bad email would report only the name.
- Constitution VI says commands validate their own invariants. Keeping the rules only in the command avoids
  writing the same regex twice.
- The command can be unit-tested without Spring (Principle II).

**Alternatives considered**:
- *Bean Validation on the DTO plus a custom `@Constraint` for "password ≠ username"*: duplicates rules that the
  command must still enforce, and puts the business rule in the adapter.
- *Fail on the first error*: violates FR-014.

## R2. Error response shape

**Decision**: both errors are RFC 9457 Problem Details (`application/problem+json`), like the rest of the API
(constitution "Errors").
- **400** `title: "Invalid registration"`. `detail` joins the messages in the existing style
  (`"field: message; field: message"`, as `GlobalExceptionHandler.handleValidation` does). An extension member
  `errors: [{ "field": "password", "message": "..." }]` makes them machine-readable.
- **409** `title: "Registration conflict"`, for example `detail: "email already registered"`, with the extension
  member `conflicts: ["username", "email"]`.

Field names in both are the JSON property names (`username`, `password`, `name`, `email`, `phone`). Full examples
are in [contracts/http-api.md](./contracts/http-api.md).

**Rationale**: the spec requires naming each field (FR-014, FR-020) "in the same error format the rest of the
system uses". The `detail` string keeps today's style; the arrays let a client highlight fields without parsing
text. `TripExceptionHandler` already sets extension properties (`activeTripId`), so this is an established pattern.

**Alternatives considered**: a custom error JSON (breaks the "always Problem Details" rule); `detail` text only
(clients would have to parse prose).

## R3. Password length: characters vs. BCrypt's 72 bytes

**Decision**: the password must have **8–72 characters** (counted as Unicode code points, so an emoji is one
character) **and at most 72 bytes in UTF-8**. If it exceeds 72 bytes, the error says "must be at most 72 bytes;
non-ASCII characters count as more than one". The password is never trimmed.

**Rationale**: BCrypt hashes only the first 72 bytes. Spring Security's `BCryptPasswordEncoder` (6.3+, which Boot
4.1 ships) throws `IllegalArgumentException` above that limit, and older versions silently truncate. The spec says
"72 characters" to avoid silent truncation. For ASCII the two limits are the same; for `é`, `ü`, or `王`, a
72-character password can be 144–216 bytes and would crash the encoder. Checking bytes in the command turns that
crash into a clean 400.

**Alternatives considered**:
- *Pre-hash with SHA-256 before BCrypt*: removes the limit, but the stored hash is no longer one that the existing
  login (`PasswordHasher.compare`) verifies. Changing login is out of scope (FR-022).
- *Characters only*: crashes on long non-ASCII passwords.

**Spec follow-up**: done. FR-005 now says "and at most 72 bytes when encoded as UTF-8".

## R4. Guaranteeing uniqueness, including under races

**Decision**: two layers.
1. **Pre-check in the service**: `existsByUsername`, `existsByEmail`, `existsByPhone`. If any is taken, throw
   `RegistrationConflictException` with *every* taken field, which satisfies FR-020's "name each of them" in the
   normal case.
2. **Database guarantee**: unique constraints/indexes on all three columns. `JpaUserRepository.add` calls
   `saveAndFlush`, catches `DataIntegrityViolationException`, maps the constraint name to the field
   (`users_username_key` → `USERNAME`, `uq_users_email` → `EMAIL`, `uq_users_phone` → `PHONE`), and throws
   `RegistrationConflictException`. It walks the cause chain the same way `JpaTripRepository.violates` does. Any
   other violation propagates.

`users_username_key` is the name PostgreSQL generates for V1's inline `UNIQUE`. The IT asserts it, so a rename
fails loudly.

**Rationale**: this is the pattern feature 001 established for active trips (pre-check, then constraint
translation in the adapter). Two simultaneous registrations both pass the pre-check; the index rejects the second
(FR-004).

**Known limit**: in a race, PostgreSQL reports only the first violated constraint, so a concurrent request
colliding on two fields gets a 409 naming one of them. FR-020 still holds in the sense that the request is rejected
with a correct field. The spec's "name each" case is the non-race one, covered by layer 1.

**Alternatives considered**: `SELECT … FOR UPDATE` or an advisory lock (heavier, and still needs the index);
`INSERT … ON CONFLICT` (a native query, and it can't tell which field conflicted without extra queries).

## R5. Email rule and normalization

**Decision**: trim, then require: at most 254 characters; exactly one `@`; a non-empty local part of at most 64
characters with no whitespace; a domain of dot-separated labels (letters, digits, hyphens, not starting or ending
with a hyphen) with at least one dot. Then store it **lowercased**. The rule is a single regex in
`RegisterUserCommand`. In the database, `uq_users_email` is a unique index on `email`, and a `CHECK (email =
lower(email))` constraint keeps any future writer from storing mixed case past the index.

**Rationale**: the spec wants case-insensitive uniqueness. Storing lowercase and indexing the plain column is
simpler than a `lower(email)` expression index, and the derived `existsByEmail` query stays index-backed.
Jakarta's `@Email` is not used: it is a framework annotation (not allowed in `core`, Principle VIII), and it
accepts `budi@localhost`.

**Alternatives considered**: full RFC 5322 parsing (accepts addresses that real users never have and that cause
support issues); a `citext` column (needs a PostgreSQL extension).

## R6. Transaction scope and BCrypt cost

**Decision**: `RegistrationService.register` is `@Transactional`, covering pre-check, hash, and insert, the same
as `AuthService.login`, which compares hashes inside its transaction.

**Rationale**: BCrypt at strength 14 takes about 1 s. Nothing is locked during it: the `exists` reads take no row
locks. The cost is one pool connection held for that second (pool size 10). This matches login, keeps one
transaction per use case (Principle VIII), and meets SC-005 (2 s at p95).

**Alternatives considered**: hashing before the transaction starts (needs `TransactionTemplate` or a second bean
to avoid self-invocation; worth it only if registration volume grows, and it would be a change to login as well).

## R7. `created_at`

**Decision**: keep the database default (`DEFAULT NOW()`) and the existing `insertable = false` mapping. Only the
Javadoc changes ("the application never inserts users" is no longer true).

**Rationale**: FR-015 only requires recording the time. No rule, response, or event reads it, so constitution
VIII's requirement to inject a `Clock` ("time that affects behavior") does not apply. Changing the column type to
`TIMESTAMPTZ` to match `sessions` would rewrite existing data and is not needed for this feature.

**Alternatives considered**: setting it from `Clock` (needs a column type change or a `LocalDateTime` conversion
in the service, for no behavioral gain).

## R8. Limits are constants, not configuration

**Decision**: 3–50 username characters, 8–72 password characters and 72 bytes, 1–100 name characters, 254 email
characters, and 8–15 phone digits are `static final` constants on `RegisterUserCommand`.

**Rationale**: they are rules fixed by the spec and partly by the schema (`name VARCHAR(100)`), not operational
tuning. Constitution V externalizes "tunable values" only, and YAGNI applies.

## R9. Existing accounts without email or phone

**Decision**: `email` and `phone` are nullable columns. PostgreSQL unique indexes allow many `NULL`s, so legacy
rows don't conflict. Every new registration supplies both, enforced by the command.

**Rationale**: the spec says they stay optional for existing accounts. A `NOT NULL` column would need invented
placeholder values for existing rows.

## R10. Missing or malformed request body

**Decision**: add an `HttpMessageNotReadableException` handler to `GlobalExceptionHandler`. It returns a Problem
Details `400` with `title: "Malformed request"` and a fixed detail ("Request body is missing or is not valid
JSON"). It does not echo parser output, which could contain the password.

**Rationale**: today this falls through to Spring's default `/error` JSON, which is not Problem Details. The spec
lists a malformed body as an edge case. The handler applies to every endpoint; that is a small, consistent
improvement worth a CHANGELOG line.

## Additional notes

- **Logging**: `RegistrationService` logs `info` "user registered, id={}, username={}" on success and `info`
  "registration conflict, fields={}" on conflict. It never logs the password, email, or phone (FR-009, SC-004).
- **Extra JSON fields** (spec edge case "attempt to set a role"): Jackson ignores unknown properties by default
  in Spring Boot, and `RegisterRequest` has no role field. A web-layer test sends `"role": "DRIVER"` to prove it.
- **No agent-context script**: `.specify/scripts/bash/` has no `update-agent-context.sh` and the repo has no
  `CLAUDE.md` or `AGENTS.md`, so the agent-context step is skipped.
