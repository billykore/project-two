# Changelog

All notable changes to this project are recorded here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

No version has been released or tagged yet, so entries are grouped by date. Add new changes under
**Unreleased**; when a version is tagged, rename that section to the version number and date.

## [Unreleased]

### Added

- OpenAPI 3.1 documentation generated from the controllers with springdoc (`springdoc-openapi-starter-webmvc-ui`
  3.2.0). It covers every endpoint, Problem Details error responses with examples, and public versus Bearer-protected
  operations. `docs/openapi.yaml` is checked in, and `OpenApiDocumentTest` keeps it identical to the generated
  document. With `APP_API_DOCS_ENABLED=true` (off by default), the app serves `/v3/api-docs`, `/v3/api-docs.yaml`,
  and Swagger UI at `/swagger-ui.html`; these paths are public and ignore a Bearer token. Plan in
  `specs/004-openapi-docs/`.
- Forgot password: `POST /auth/password-reset/request` sends a 6-digit one-time code to the email address or phone
  number of an account (always answering `202`, so it never reveals which contacts exist), and
  `POST /auth/password-reset/confirm` replaces the password with it. Codes last 5 minutes, work once, and are
  invalidated after 5 wrong attempts; an account gets at most 1 code per 60 seconds and 5 per hour. The new password
  follows the registration rules and may not be one of the last 3 passwords. A successful reset ends the account's
  sessions. Codes are stored only as an HMAC hash, which needs the new secret `APP_RESET_CODE_SECRET`. Migration
  `V12` adds `password_reset_codes` and `password_history`. Delivery is a development stub
  (`APP_PASSWORD_RESET_LOG_CODES=true` logs the code); real email and SMS providers are not connected yet. Specs in
  `specs/003-forgot-password/`.
- `POST /auth/register`: self-service customer registration with a username, password, name, email address, and
  phone number. Usernames, emails (ignoring case), and phone numbers are unique, including under simultaneous
  requests; invalid input and conflicts are answered with Problem Details naming each field. Migration `V11` adds
  nullable `email` and `phone` columns with unique indexes to `users`. Specs in `specs/002-user-registration/`.
- Testcontainers-based tests (PostgreSQL 16, Kafka 3.9). Running `./mvnw test` now requires a container runtime
  such as Docker; no hand-started PostgreSQL or Kafka is needed.
- Golden JSON fixtures for every trip event payload (`src/test/resources/events/`), so a change to an event's JSON
  fails the build.
- `ArchitectureTest` (ArchUnit) enforcing the hexagonal dependency rules AR-01..AR-09, plus a self-test proving each
  rule fires.
- Tests for every layer: web-layer tests for all four controllers, PostgreSQL integration tests for every persistence
  adapter (including two concurrent trip requests for one customer), a Kafka integration test for event delivery,
  tests for the JWT, BCrypt and SHA-256 adapters, and unit tests for `OfferService` and `AuthService`.
- Spec Kit artifacts for the refactor in `specs/001-hexagonal-architecture-compliance/` (spec, plan, research, data
  model, contracts, tasks).

### Changed

- The registration password and contact rules (email, phone) are now shared with the password reset through
  `PasswordRules` and `ContactRules`; registration behaves and answers exactly as before. `FieldError` holds the
  client-facing field name instead of an enum.
- Java 25 (the current LTS release) replaces Java 17: `java.version` in `pom.xml`, the CI JDK, and the README
  prerequisites. Building now needs JDK 25 or newer.
- Imports are explicit: all wildcard imports (`import lombok.*;`, `import jakarta.persistence.*;`, …) were replaced
  by the classes each file uses. `ImportStyleTest` fails the build if one comes back, and `.editorconfig` stops IDEs
  from generating them.
- A missing or non-JSON request body now gets a `400` Problem Details response titled `Malformed request` on every
  endpoint, instead of Spring's default error body.
- The code now follows hexagonal architecture (ports and adapters), enforced by `ArchitectureTest` (ArchUnit): ports in
  `core/port` are plain interfaces implemented by adapters grouped per technology (`adapter/persistence`,
  `adapter/kafka`, `adapter/security`); trip-creation rules live in `Trip.request`; time and ids come from an injected
  `Clock` and `IdGenerator`; the `common` package is gone. No endpoint or event payload changed.
- "Service type" has a single definition, the `service_types` table: `POST /trips` looks the name up there instead
  of mapping it through a separate enum. Stored ids and responses are unchanged.
- Tests no longer depend on a developer's `.env`: test-only settings come from
  `src/test/resources/config/application.properties`.
- Project constitution amended to 2.0.1: clarifies where adapter-specific `@ConfigurationProperties` live and that
  provider-specific persistence mapping annotations (such as Hibernate's) are allowed on entities.
- Offer events (`DriverAssigned`, `Rejected`) are now published only after the database transaction commits, like
  `Requested`. Previously they were sent while the transaction was still open, so an event could describe a change
  that was then rolled back.

### Removed

- The unused trip `idempotencyKey`: the API filled it with a random value on every request and nothing read it, so it
  never deduplicated anything.

### Fixed

- `/auth/**` (login, registration, password reset) now ignores a Bearer token sent with the request. Before, an
  expired or invalid token turned these public requests into `401 Unauthorized`; protected endpoints still reject
  such tokens.
- A token without a numeric `sub` claim now gets `401 Unauthorized` on `POST /trips` and
  `POST /driver/offers/{tripId}/accept`. Previously `POST /trips` tried to book the trip for customer `0` and both
  endpoints could fail with `500`.
- Flyway migrations now actually run on startup. Spring Boot 4 moved Flyway auto-configuration into its own module,
  and the project depended only on `flyway-core`, so a fresh database was never migrated and the application failed
  schema validation. An existing database without a `flyway_schema_history` table must be recreated or baselined
  once (see README, "Run the application").
- `README.md` now explains that `POST /trips` takes the service type name (`MyRide`, `MyCar`), not the database code
  (`MYRIDE`, `MYCAR`).
- The 2026-11-11 entry about one unfinished trip per customer now lists `ON_TRIP` alongside the other statuses.

## 2026-11-11

### Added

- Trip booking: `POST /trips` lets a customer book a MyRide (motorcycle) or MyCar (car) trip with a pickup point,
  destination, and payment method (`CASH`, `CREDIT_CARD`, `QRIS`).
- Driver offers: `POST /driver/offers/{tripId}/accept` lets a driver accept a pending, unexpired trip offer.
- Fare calculation from the straight-line (haversine) distance between pickup and destination, with a configurable
  per-meter rate (`app.fare.per-meter`) and minimum fare (`app.fare.minimum`, default IDR 10,000).
- Trip domain events (`Requested`, `DriverAssigned`, `Rejected`, `Completed`, `Cancelled`) published as JSON to the
  Kafka topic `trip-events` after the database transaction commits, keyed by trip id.
- Database migrations V8 (`trip_status_history`), V9 (`trip_offers`), and V10 (unique index allowing one unfinished
  trip per customer).
- `400 Invalid trip` and `409 Active trip exists` problem responses for trip requests.
- Unit tests for distance calculation, trip creation, and driver assignment.
- `README.md` describing the project, architecture, setup, configuration, and API.
- `.env.example` listing the environment variables the application needs.

### Changed

- Trip status `ACTIVE` renamed to `ON_TRIP` to match the database constraint.
- `GlobalExceptionHandler` moved from `common` to `api.handler`.
- Database credentials, the JWT secret, and the Kafka address are now read from environment variables or a
  git-ignored `.env` file instead of `application.properties`.

### Fixed

- Accepting an offer now moves the trip to `DRIVER_ASSIGNED` and records `driver_assigned_at`; accepting a trip that
  is no longer `REQUESTED` returns `409`.
- A customer can no longer book a second trip while one is unfinished (`REQUESTED`, `DRIVER_ASSIGNED`,
  `DRIVER_ARRIVED`, or `ON_TRIP`), including when two requests arrive at the same time. Previously only `ACTIVE`
  (now `ON_TRIP`) was checked.
- An unknown `serviceType` returns `400` instead of a `500` from the `service_types` foreign key.
- Booking a trip no longer fails with a `500` because the distance was always `0`.

### Security

- The JWT secret committed in the initial commit is public in the git history and has been replaced; it must not be
  used anywhere.
- `.gitignore` now excludes `.env` files, keys and certificates, credential files, logs and dumps, and OS files.

## 2026-10-26

### Added

- Initial Spring Boot 4.1 project (Java 17, Maven) with a ports-and-adapters layout.
- Login with `POST /auth/login`: BCrypt password check, HS256 JWT access token, and a session record storing a
  SHA-256 hash of the token.
- Stateless JWT authentication for every endpoint except `/auth/**`, with JSON `401` responses.
- `GET /hello/{username}` sample endpoint.
- RFC 9457 problem-detail error responses.
- PostgreSQL schema managed by Flyway: `users`, `sessions`, `driver_profiles`, `service_types` (seeded with `MYRIDE`
  and `MYCAR`), and `trips`.
