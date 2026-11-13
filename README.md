# myapp — Ride-Hailing Backend

A backend service for a ride-hailing app in the style of Gojek or Grab: customers book a trip (motorcycle taxi or car),
the system offers the trip to drivers, and a driver accepts the offer. The project is also a way to learn Spring Boot
with a clean architecture (ports & adapters), JWT, Flyway, and event-driven messaging through Kafka.

> **Status:** work in progress. See [Feature status](#feature-status) for what works and what does not yet.

## Main features

- **Registration** — sign up with a username, password, name, email address, and phone number; usernames, emails,
  and phone numbers are unique.
- **Authentication** — log in with username and password (BCrypt), receive a JWT (HS256), and record the session in
  the database.
- **Forgot password** — request a one-time code by email or phone, then replace the password with it. The new
  password follows the registration rules and may not be one of the last 3 passwords. A reset ends the account's
  sessions. Delivery is a development stub for now (see [Forgot password](#forgot-password)).
- **Trip booking** — a customer creates a trip with a pickup point, destination, service type, and payment method.
  A customer can have at most one unfinished trip, enforced by both the service and a unique index in the database.
- **Trip offers** — a driver accepts a trip offer; the trip moves to `DRIVER_ASSIGNED` and the driver's status
  changes to `ON_TRIP`.
- **Domain events** — every important trip change is published to Kafka after the database transaction commits.
- **Fares** — computed from the straight-line distance between pickup and destination (haversine formula) times a
  per-meter rate, with a minimum fare.

### Service types

| Code     | Name   | Vehicle    | Max. passengers |
|----------|--------|------------|-----------------|
| `MYRIDE` | MyRide | Motorcycle | 1               |
| `MYCAR`  | MyCar  | Car        | 4               |

The code is what is stored in the `service_types` table. The API takes the **name** instead: `POST /trips` expects
`"serviceType": "MyRide"` or `"MyCar"` (case-sensitive), and any other value, including `MYRIDE`, is rejected with
`400`.

Payment methods: `CASH`, `CREDIT_CARD`, `QRIS`.

## Tech stack

- Java 25, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server)
- PostgreSQL 13+ with Flyway migrations
- Apache Kafka for domain events
- Lombok
- Maven (via `mvnw`)

## Architecture

The code follows a *hexagonal / ports & adapters* architecture. The domain and its ports sit in the middle, adapters
for HTTP, PostgreSQL, Kafka and JWT sit around the edge, and every source-code dependency points inward.

```
src/main/java/org/kore/myapp
├── core/                 # The hexagon: no Spring, Kafka, servlet or JSON types
│   ├── entity/           #   Entities and their invariants: Trip, TripOffer, DriverProfile, ServiceType, User, Session
│   ├── valueobject/      #   Immutable values, commands and events: Location, Money, CreateTripCommand, TripEvent, ...
│   ├── domainservice/    #   Rules built from plain values: FareCalculator
│   ├── exception/        #   Domain exceptions
│   └── port/             #   Inbound ports (*UseCase) and outbound ports (TripRepository, EventPublisher, IdGenerator, ...)
├── service/              # Application services: implement the use cases, own the transactions
├── api/                  # Inbound HTTP adapter
│   ├── controller/       #   Controllers: map a request to a command, call one use case, map the result
│   ├── request/, response/  # DTOs
│   ├── handler/          #   Domain exceptions → RFC 9457 Problem Details; JSON 401 entry point
│   └── security/         #   Reads the caller's user id from the JWT
├── adapter/              # Outbound adapters, one package per technology
│   ├── persistence/      #   PostgreSQL via Spring Data JPA; translates constraint violations into domain exceptions
│   ├── kafka/            #   Publishes events to Kafka after the transaction commits; decides the message JSON
│   └── security/         #   JWT issuing, BCrypt password hashing, SHA-256 token hashing
└── config/               # Composition root: Spring wiring, security, clock, id generator, configuration properties
```

| Package    | May depend on                                    |
|------------|--------------------------------------------------|
| `core`     | nothing else in the application                  |
| `service`  | `core`                                           |
| `api`      | `core` (use-case ports only, never outbound ports) |
| `adapter`  | `core`; each adapter package independently of the others |
| `config`   | anything (it wires everything together)          |

These rules come from the project constitution (`.specify/memory/constitution.md`, Principles IV, VI, VII, VIII) and
are enforced by `ArchitectureTest`, so a change that breaks them fails `./mvnw test`. Time and generated ids come from
an injected `Clock` and `IdGenerator`, so the domain and application services are unit-tested without any
infrastructure.

### Booking flow

```
Customer                  Backend                              Kafka (trip-events)       Driver
    │  POST /trips           │                                        │                     │
    ├───────────────────────►│ save trip (REQUESTED)                  │                     │
    │                        │ ── after commit ────────────────────►  │ TripEvent.Requested │
    │◄───────── 201 ─────────┤                                        │                     │
    │                        │        POST /driver/offers/{tripId}/accept                   │
    │                        │◄─────────────────────────────────────────────────────────────┤
    │                        │ trip → DRIVER_ASSIGNED,                │                     │
    │                        │ offer → ACCEPTED, driver → ON_TRIP     │                     │
    │                        │ ── after commit ─────────────────────► │ DriverAssigned      │
```

Trip lifecycle: `REQUESTED` → `DRIVER_ASSIGNED` → `DRIVER_ARRIVED` → `ON_TRIP` → `COMPLETED`, or `CANCELLED`
(by `CUSTOMER`, `DRIVER`, or `SYSTEM`). Every status other than `REQUESTED` and `CANCELLED` requires a driver.

Events are sent to the `trip-events` topic as JSON keyed by `tripId`, so all events of one trip land in the same
partition and keep their order. Event types: `Requested`, `DriverAssigned`, `Rejected`, `Completed`, `Cancelled`.

Every event is sent only after the database transaction that caused it commits, so no event is ever published for
a change that was rolled back. If Kafka can't be reached at that point, the failure is logged and the request still
succeeds; the event is not retried.

## Running locally

### Prerequisites

- JDK 25+
- Docker (for PostgreSQL and Kafka), or local installations of both

### 1. Start PostgreSQL

```bash
docker run -d --name myapp-postgres -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:16
```

### 2. Start Kafka

```bash
docker run -d --name myapp-kafka -p 9092:9092 apache/kafka:3.9.0
```

### 3. Configure secrets

Secrets are not committed. Copy the template and fill in the values:

```bash
cp .env.example .env
```

```bash
echo "APP_JWT_SECRET=$(openssl rand -base64 32)" >> .env
```

```bash
echo "APP_RESET_CODE_SECRET=$(openssl rand -base64 32)" >> .env
```

Set `DB_PASSWORD` in `.env` too (`postgres` for the container above). `.env` is git-ignored and read by Spring on
startup; real environment variables with the same names take precedence.

### 4. Run the application

```bash
./mvnw spring-boot:run
```

Flyway runs the migrations in `src/main/resources/db/migration` on startup, including the seed for the `MYRIDE` and
`MYCAR` service types. The application listens on `http://localhost:8080`.

> **Existing databases:** if your database already has the tables but no `flyway_schema_history` table (for example
> because it was set up before Flyway ran on startup), Flyway refuses to start against the non-empty schema. Either
> recreate the database, or baseline it once with `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true ./mvnw spring-boot:run`.

### 5. Create a user to log in with

Register through the API (see [Register](#register) for the rules):

```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username": "budi", "password": "secret123", "name": "Budi Santoso", "email": "budi@example.com", "phone": "+6281234567890"}'
```

Registered users are customers. To let a user act as a driver, also add a row to `driver_profiles` with
`verification_status` set to `APPROVED`; there is no endpoint for driver onboarding yet.

## Configuration

Configuration lives in `src/main/resources/application.properties`. Secrets and machine-specific values are read from
environment variables or a local `.env` file (see `.env.example`):

| Variable                  | Default                                     | Description                                |
|---------------------------|---------------------------------------------|--------------------------------------------|
| `DB_URL`                  | `jdbc:postgresql://localhost:5432/postgres` | PostgreSQL connection                      |
| `DB_USERNAME`             | —                                           | PostgreSQL user                            |
| `DB_PASSWORD`             | —                                           | PostgreSQL password                        |
| `APP_JWT_SECRET`          | —                                           | HMAC secret, Base64, at least 32 bytes     |
| `APP_RESET_CODE_SECRET`   | —                                           | HMAC key for password-reset codes, Base64, at least 32 bytes (`openssl rand -base64 32`); the app does not start without it |
| `APP_PASSWORD_RESET_LOG_CODES` | `false`                                | Development only: write reset codes to the application log. Leave `false` in production |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`                            | Kafka broker address                       |

Other properties:

| Property                         | Default                                     | Description                                |
|----------------------------------|---------------------------------------------|--------------------------------------------|
| `app.jwt.issuer`                 | `myapp`                                     | `iss` claim of the JWT                     |
| `app.jwt.access-token-ttl`       | `10s`                                       | Access token lifetime                      |
| `app.kafka.trip-events-topic`    | `trip-events`                               | Topic for trip domain events               |
| `app.kafka.publish-timeout`      | `5s`                                        | How long to wait for a Kafka ack           |
| `app.fare.per-meter`             | `30`                                        | Rate per meter (IDR)                       |
| `app.fare.minimum`               | `10000`                                     | Minimum fare (IDR)                         |
| `app.password-reset.code-ttl`    | `5m`                                        | How long a reset code stays valid          |
| `app.password-reset.max-attempts` | `5`                                        | Wrong codes before the current code is invalidated |
| `app.password-reset.resend-interval` | `60s`                                   | Minimum time between two codes for one account |
| `app.password-reset.max-codes-per-hour` | `5`                                  | Codes one account may be sent in a rolling hour |
| `app.password-reset.lock-timeout` | `15s`                                      | How long a reset waits for another reset of the same account before answering `503` |

> `access-token-ttl` is set to `10s` to make token expiry easy to test; raise it (for example to `15m`) when trying
> the API by hand.

## API

Every endpoint except `/auth/**` requires an `Authorization: Bearer <accessToken>` header. A token sent to `/auth/**` anyway is
ignored, even when it is expired or invalid. Errors are returned as
[RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`). On any endpoint, a
missing or non-JSON request body gets `400` with the title `Malformed request`.

A missing, invalid, or expired token gets `401 Unauthorized` with a `WWW-Authenticate: Bearer` header. `POST /trips`
and `POST /driver/offers/{tripId}/accept` also answer `401` when an otherwise valid token has no numeric user id in
its `sub` claim, since the request can't be attributed to a user. Tokens issued by `/auth/login` always carry one.

| Method | Path                               | Description                                  |
|--------|------------------------------------|----------------------------------------------|
| `POST` | `/auth/register`                   | Create a customer account                    |
| `POST` | `/auth/login`                      | Log in and receive an access token           |
| `POST` | `/auth/password-reset/request`     | Send a one-time reset code by email or phone |
| `POST` | `/auth/password-reset/confirm`     | Replace the password using the code          |
| `POST` | `/trips`                           | Customer books a trip                        |
| `POST` | `/driver/offers/{tripId}/accept`   | Driver accepts a trip offer                  |
| `GET`  | `/hello/{username}`                | Sample endpoint for checking a token         |

### Register

```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username": "budi", "password": "secret123", "name": "Budi Santoso", "email": "budi@example.com", "phone": "+6281234567890"}'
```

All five fields are required:

| Field      | Rule                                                                                               |
|------------|----------------------------------------------------------------------------------------------------|
| `username` | 3-50 characters: lowercase letters, digits, `.`, `_`, `-`; unique                                  |
| `password` | 8-72 characters and at most 72 bytes in UTF-8; not the same as the username; spaces are kept      |
| `name`     | 1-100 characters after trimming                                                                    |
| `email`    | valid address, at most 254 characters; stored in lowercase; unique, ignoring case                  |
| `phone`    | international form, `+` and 8-15 digits, such as `+6281234567890`; unique                          |

`201 Created` returns the new account (never the password) and no token; log in separately:

```json
{ "id": 42, "username": "budi", "name": "Budi Santoso", "email": "budi@example.com", "phone": "+6281234567890" }
```

- `400` (`Invalid registration`) lists every invalid field in `errors`: `[{"field": "password", "message": "must be at
  least 8 characters"}]`.
- `409` (`Registration conflict`) lists the fields already in use in `conflicts`, for example `["username", "email"]`.
  Email and phone numbers are not verified, and logging in still uses the username only.

### Forgot password

Two public steps. First ask for a code with the email address or the phone number of the account (exactly one of
the two):

```bash
curl -X POST http://localhost:8080/auth/password-reset/request \
  -H 'Content-Type: application/json' \
  -d '{"email": "budi@example.com"}'
```

It always answers `202` with `{"message": "If an account exists for this contact, a code has been sent to it."}`,
whether or not an account matches, so it never reveals which contacts are registered. A code is 6 digits, valid for 5
minutes, and usable once. A new code replaces the previous one. An account gets at most 1 code per 60 seconds and 5
per hour; requests over the limit still answer `202` but send nothing.

Then send the code with the new password. `email` or `phone` may be either contact detail of the account, whichever
one received the code:

```bash
curl -X POST http://localhost:8080/auth/password-reset/confirm \
  -H 'Content-Type: application/json' \
  -d '{"email": "budi@example.com", "code": "004213", "newPassword": "newsecret99"}'
```

`204 No Content` means the password was replaced, the code is used up, and every session of the account has ended.
No token is issued; log in again.

- `400` (`Invalid password reset`) lists every invalid field in `errors`, like registration: a malformed contact or
  code (`must be 6 digits`), or a new password that breaks the registration rules, equals the username
  (`must not be the same as the username`), or is one of the account's last 3 passwords, the current one included
  (`must not be one of your last 3 passwords`). A refused password does not use up the code or count as a wrong code.
- `400` (`Invalid or expired code`) is the single answer for a wrong, expired, used, replaced, or exhausted code and
  for an unknown contact. After 5 wrong codes the current code is invalidated.
- `503` (`Password reset busy`, with `Retry-After: 1`) means another reset of the same account was still running; try
  again.

**Delivery is not connected to a real email or SMS provider yet.** By default nothing is sent and the request
completes without a code. For development, set `APP_PASSWORD_RESET_LOG_CODES=true` and the code is written to the
application log (look for `password reset code for EMAIL …`). Never enable it in production.

### Log in

```bash
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username": "budi", "password": "secret123"}'
```

```json
{
  "sessionId": "5b0c6f0e-6f5a-4d6e-9a59-1f1f0f3f4a11",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-11-11T09:00:10Z"
}
```

### Book a trip

```bash
curl -X POST http://localhost:8080/trips \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "serviceType": "MyRide",
        "pickup":      {"latitude": -6.2088, "longitude": 106.8456, "address": "Monas"},
        "destination": {"latitude": -6.2250, "longitude": 106.8020, "address": "Senayan"},
        "paymentMethod": "QRIS"
      }'
```

Responds with `201 Created` and a `Location: /trips/{id}` header:

```json
{ "id": "3f8a...", "status": "REQUESTED", "serviceType": 1 }
```

Possible errors:

- `400 Bad Request` — validation failed, `serviceType` is not exactly `MyRide` or `MyCar`, or pickup and destination are the
  same place.
- `409 Conflict` — the customer still has an unfinished trip (`REQUESTED`, `DRIVER_ASSIGNED`, `DRIVER_ARRIVED`, or
  `ON_TRIP`). The response includes `activeTripId`, except when the other trip was created by a concurrent request.

### Driver accepts an offer

```bash
curl -X POST http://localhost:8080/driver/offers/$TRIP_ID/accept \
  -H "Authorization: Bearer $DRIVER_TOKEN"
```

Returns the trip with status `DRIVER_ASSIGNED`. If the trip is no longer `REQUESTED` (for example it was cancelled or
another driver took it), the response is `409 Conflict`.

## Database schema

| Table                 | Contents                                                                      |
|-----------------------|-------------------------------------------------------------------------------|
| `users`               | User accounts (customers and drivers), with unique username, email, and phone |
| `sessions`            | Login sessions; stores a SHA-256 hash of the token, not the token itself      |
| `password_reset_codes` | One-time password-reset codes (HMAC hash only), with attempts, expiry, and when used or invalidated |
| `password_history`    | The two passwords before the current one (hashes), to refuse reuse            |
| `driver_profiles`     | Driver data: license, verification status, availability (`OFFLINE`/`ONLINE`/`ON_TRIP`) |
| `service_types`       | Service types (`MYRIDE`, `MYCAR`)                                             |
| `trips`               | Trip bookings, including a snapshot of distance and fare at booking time      |
| `trip_status_history` | History of trip status changes                                                |
| `trip_offers`         | Trip offers to drivers and when they expire                                   |

`trips` and `driver_profiles` use a `version` column for optimistic locking, and key business rules (for example, a
driver must be `APPROVED` before going `ONLINE`) are also enforced by database constraints.

## Testing

```bash
./mvnw test
```

The test suite needs a running container runtime (Docker Desktop, Colima, or Podman with a Docker-compatible
socket). Integration tests start PostgreSQL 16 and Kafka 3.9 with [Testcontainers](https://testcontainers.com/), so you
don't need to start PostgreSQL or Kafka yourself to run the tests. Without a container runtime those tests fail rather
than skip.

The tests never read your `.env`: test-only settings, including a fake JWT signing key, come from
`src/test/resources/config/application.properties`.

| Kind | Examples | Needs |
|------|----------|-------|
| Domain and application-service unit tests | `TripTest`, `FareCalculatorTest`, `TripServiceTest`, `OfferServiceTest` | nothing (fixed `Clock` and `IdGenerator`) |
| Architecture test | `ArchitectureTest`, `ArchitectureRulesSelfTest` | nothing |
| Web-layer tests, one per controller | `TripControllerTest`, `AuthControllerTest`, … | nothing (`@WebMvcTest`) |
| Persistence adapter integration tests | `JpaTripRepositoryIT`, `JpaPasswordResetCodeRepositoryIT`, … | PostgreSQL container |
| Password reset end to end | `PasswordResetFlowIT` (request, confirm, limits, history, sessions) | PostgreSQL and Kafka containers |
| Event adapter integration test | `AfterCommitEventPublisherIT` | Kafka container |
| Event payload contract | `EventJsonContractTest` against `src/test/resources/events/*.json` | nothing |

`ArchitectureTest` fails the build when a change breaks the hexagonal dependency rules described in
[Architecture](#architecture).

## Feature status

Done:

- [x] Login and JWT issuance
- [x] Trip booking, limited to one active trip per customer
- [x] Straight-line distance and fare calculation
- [x] Driver accepts a trip offer
- [x] Publishing domain events to Kafka after commit
- [x] Hexagonal architecture enforced by an architecture test
- [x] Integration tests against real PostgreSQL and Kafka (Testcontainers) for the persistence and event adapters
- [x] Web-layer tests for every endpoint
- [x] Customer registration with unique username, email, and phone
- [x] Forgot password with a one-time code (development delivery only)

Not yet:

- [ ] Real email and SMS delivery of reset codes
- [ ] Driver onboarding
- [ ] Road-route distance (currently a straight line, so shorter than the real distance)
- [ ] Finding the nearest drivers and creating `trip_offers`
- [ ] Endpoints to reject an offer, mark driver arrival, and start, complete, or cancel a trip
- [ ] A scheduler to expire stale offers
- [ ] Refresh tokens and logout (a password reset already ends sessions, but issued access tokens stay valid until they expire)
- [ ] Idempotency key on trip creation
