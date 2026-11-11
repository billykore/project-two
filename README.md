# myapp — Ride-Hailing Backend

A backend service for a ride-hailing app in the style of Gojek or Grab: customers book a trip (motorcycle taxi or car),
the system offers the trip to drivers, and a driver accepts the offer. The project is also a way to learn Spring Boot
with a clean architecture (ports & adapters), JWT, Flyway, and event-driven messaging through Kafka.

> **Status:** work in progress. See [Feature status](#feature-status) for what works and what does not yet.

## Main features

- **Authentication** — log in with username and password (BCrypt), receive a JWT (HS256), and record the session in
  the database.
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

Payment methods: `CASH`, `CREDIT_CARD`, `QRIS`.

## Tech stack

- Java 17, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server)
- PostgreSQL 13+ with Flyway migrations
- Apache Kafka for domain events
- Lombok
- Maven (via `mvnw`)

## Architecture

The code follows a *hexagonal / ports & adapters* style. Business logic lives in `core` and `service`, while technical
details (HTTP, Kafka, JWT, BCrypt) sit at the edges.

```
src/main/java/org/kore/myapp
├── api/          # HTTP layer: controllers, request/response DTOs, exception handlers (ProblemDetail)
├── service/      # Use case implementations: AuthService, TripService, OfferService
├── core/
│   ├── entity/       # JPA entities: User, Session, DriverProfile, ServiceType, Trip, TripOffer
│   ├── valueobject/  # Enums & value objects: TripStatus, OfferStatus, Location, Money, TripEvent, ...
│   ├── port/         # Use case & repository interfaces (ports)
│   ├── event/        # TripEventRelay: forwards events to Kafka after commit
│   └── exception/    # Domain exceptions
├── adapter/      # Port implementations: KafkaPublisher, JwtTokenIssuer, BcryptHasher, JSON 401 entry point
├── config/       # SecurityConfig, FareProperties
└── common/       # FareCalculator, ProblemDetailWriter
```

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
    │                        │ ─────────────────────────────────────► │ DriverAssigned      │
```

Trip lifecycle: `REQUESTED` → `DRIVER_ASSIGNED` → `DRIVER_ARRIVED` → `ON_TRIP` → `COMPLETED`, or `CANCELLED`
(by `CUSTOMER`, `DRIVER`, or `SYSTEM`). Every status other than `REQUESTED` and `CANCELLED` requires a driver.

Events are sent to the `trip-events` topic as JSON keyed by `tripId`, so all events of one trip land in the same
partition and keep their order. Event types: `Requested`, `DriverAssigned`, `Rejected`, `Completed`, `Cancelled`.

## Running locally

### Prerequisites

- JDK 17+
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

Set `DB_PASSWORD` in `.env` too (`postgres` for the container above). `.env` is git-ignored and read by Spring on
startup; real environment variables with the same names take precedence.

### 4. Run the application

```bash
./mvnw spring-boot:run
```

Flyway runs the migrations in `src/main/resources/db/migration` on startup, including the seed for the `MYRIDE` and
`MYCAR` service types. The application listens on `http://localhost:8080`.

### 5. Create a user to log in with

There is no registration endpoint yet, so users are inserted directly into the database. The password must be a
BCrypt hash, for example generated with `htpasswd`:

```bash
htpasswd -bnBC 14 "" secret123 | tr -d ':\n'
```

```sql
INSERT INTO users (name, username, password)
VALUES ('Budi', 'budi', '<bcrypt-hash-from-the-command-above>');
```

To let a user act as a driver, also add a row to `driver_profiles` with `verification_status` set to `APPROVED`.

## Configuration

Configuration lives in `src/main/resources/application.properties`. Secrets and machine-specific values are read from
environment variables or a local `.env` file (see `.env.example`):

| Variable                  | Default                                     | Description                                |
|---------------------------|---------------------------------------------|--------------------------------------------|
| `DB_URL`                  | `jdbc:postgresql://localhost:5432/postgres` | PostgreSQL connection                      |
| `DB_USERNAME`             | —                                           | PostgreSQL user                            |
| `DB_PASSWORD`             | —                                           | PostgreSQL password                        |
| `APP_JWT_SECRET`          | —                                           | HMAC secret, Base64, at least 32 bytes     |
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

> `access-token-ttl` is set to `10s` to make token expiry easy to test; raise it (for example to `15m`) when trying
> the API by hand.

## API

Every endpoint except `/auth/**` requires an `Authorization: Bearer <accessToken>` header. Errors are returned as
[RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`).

| Method | Path                               | Description                                  |
|--------|------------------------------------|----------------------------------------------|
| `POST` | `/auth/login`                      | Log in and receive an access token           |
| `POST` | `/trips`                           | Customer books a trip                        |
| `POST` | `/driver/offers/{tripId}/accept`   | Driver accepts a trip offer                  |
| `GET`  | `/hello/{username}`                | Sample endpoint for checking a token         |

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

- `400 Bad Request` — validation failed, `serviceType` is not `MyRide` or `MyCar`, or pickup and destination are the
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
| `users`               | User accounts (customers and drivers)                                         |
| `sessions`            | Login sessions; stores a SHA-256 hash of the token, not the token itself      |
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

The unit tests (`CoordinateTest`, `TripTest`, `TripServiceTest`) run without external dependencies. `MyAppTests` loads the full
Spring context, so it needs a running PostgreSQL.

## Feature status

Done:

- [x] Login and JWT issuance
- [x] Trip booking, limited to one active trip per customer
- [x] Straight-line distance and fare calculation
- [x] Driver accepts a trip offer
- [x] Publishing domain events to Kafka after commit

Not yet:

- [ ] Customer registration and driver onboarding
- [ ] Road-route distance (currently a straight line, so shorter than the real distance)
- [ ] Finding the nearest drivers and creating `trip_offers`
- [ ] Endpoints to reject an offer, mark driver arrival, and start, complete, or cancel a trip
- [ ] A scheduler to expire stale offers
- [ ] Refresh tokens and logout (session revocation)
- [ ] Idempotency key on trip creation
- [ ] Integration tests (for example with Testcontainers)
