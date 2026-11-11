# Data Model: Hexagonal Architecture Compliance

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md) | **Date**: 2026-11-11

This feature changes no tables and adds no migrations. What changes is how the domain model is **built and where it
lives**: factories instead of public builders, ids and time passed in, one service-type concept, and a command in
`core`. Persisted columns, constraints and event payloads stay the same.

## Package roles after the refactor

| Package | Contents after this feature |
|---------|-----------------------------|
| `core.entity` | `Trip`, `TripOffer`, `DriverProfile`, `ServiceType`, `User`, `Session` (JPA mapping allowed) |
| `core.valueobject` | `Coordinate`, `Location`, `Money`, `Token`, enums (`TripStatus`, `OfferStatus`, `CancelledBy`, `PaymentMethod`, `VehicleType`, `VerificationStatus`, `AvailabilityStatus`), `Event`, `TripEvent`, **`CreateTripCommand`** (moved in) |
| `core.domainservice` | **`FareCalculator`** (moved in, plain class) |
| `core.exception` | existing domain exceptions + **`UnidentifiedCallerException`** (new) |
| `core.port` | inbound: `TripUseCase`, `TripOfferUseCase`, `AuthUseCase`, `HelloUseCase`; outbound: `TripRepository`, `TripOfferRepository`, `DriverProfileRepository`, `UserRepository`, `SessionRepository`, `ServiceTypeRepository`, `EventPublisher`, `TokenIssuer`, `PasswordHasher`, **`TokenHasher`**, **`IdGenerator`** (see [contracts/ports.md](./contracts/ports.md)) |
| `service` | `TripService`, `OfferService`, `AuthService`, `HelloService` |
| `api.controller`, `api.request`, `api.response` | unchanged files, thinner controllers |
| `api.handler` | `GlobalExceptionHandler`, `TripExceptionHandler`, **`ProblemDetailWriter`**, **`JsonAuthenticationEntryPoint`** |
| `api.security` | **`AuthenticatedCaller`** |
| `adapter.persistence` | `Jpa*Repository` port implementations + package-private `SpringData*Repository` interfaces |
| `adapter.kafka` | `AfterCommitEventPublisher`, `KafkaEventSender`, `EventJsonMixin`, `TripEventsProperties` |
| `adapter.security` | `JwtTokenIssuer`, `JwtProperties`, `BcryptPasswordHasher`, `Sha256TokenHasher` |
| `config` | `SecurityConfig`, **`ApplicationConfig`** (`Clock`, `IdGenerator`, `FareCalculator` beans), `FareProperties` |
| `common`, `core.event`, `service.command` | **removed** |

## Entities

### Trip (aggregate root): changed

| Field | Type | Change |
|-------|------|--------|
| `id` | `UUID` | No longer `@GeneratedValue`. Assigned by `Trip.request` from `IdGenerator`. |
| `customerId` | `long` | unchanged |
| `driverId` | `Long` | unchanged (null until assigned) |
| `serviceTypeId` | `long` | Set from `ServiceType.getId()` instead of `enum.ordinal()`. The values are identical for the seed data. |
| `status` | `TripStatus` | unchanged; initial value set by the factory |
| pickup and destination fields, `distanceMeters`, `fare` | | unchanged; computed by the factory |
| `requestedAt` | `Instant` | No `Instant.now()` default. Set from the `now` argument. |
| `driverAssignedAt` and other timestamps | `Instant` | unchanged |
| `version` | `Long` | Changes from `long = 0` to `Long`, where null means new, so Spring Data detects an insert for an assigned id. The column is unchanged. |
| `createdAt`, `updatedAt` | `Instant` | unchanged (Hibernate audit timestamps; allowed mapping) |

**Construction**: the only way to create a new trip is

```text
Trip.request(id, customerId, serviceType, pickup, destination, fareCalculator, now) → Trip
```

The factory enforces these rules:
- `distance = pickup.coordinate.distanceMetersTo(destination.coordinate)`. When `distance ≤ 0`, it throws
  `InvalidTripException("Pickup and destination must be different places")`.
- `fare = fareCalculator.calculateFare(distance)`.
- `status = REQUESTED`, `requestedAt = now`, `driverId = null`.

Lombok's builder becomes package-private, for JPA and tests in `core.entity` only. The public setters are removed;
state changes only through domain methods.

**State transitions** (unchanged and enforced by the entity; only `REQUESTED → DRIVER_ASSIGNED` is implemented today):

```text
REQUESTED ──assignDriver(driver, now)──► DRIVER_ASSIGNED ──► DRIVER_ARRIVED ──► ON_TRIP ──► COMPLETED
    │                                         │                   │              │
    └─────────────────────────────────────────┴───────────────────┴──────────────┴──► CANCELLED
```

`assignDriver` from any status other than `REQUESTED` throws `IllegalStateException`, which the API returns as 409
(unchanged).

### ServiceType (reference data): now the single definition

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | `MyRide` = 1, `MyCar` = 2 (seed, V6) |
| `code` | `String` | `MYRIDE`, `MYCAR` |
| `name` | `String` | `MyRide`, `MyCar`; the value the API accepts, case-sensitive |
| `vehicleType` | `VehicleType` | |
| `maxPassengers` | `int` | |
| `displayOrder` | `int` | |
| `active` | `boolean` | Only active types can be ordered |

The enum `core.valueobject.ServiceType` (`UNKNOWN`, `MY_RIDE`, `MY_CAR`) is **deleted**.

### TripOffer, DriverProfile: unchanged shape

- `TripOffer.isExpired(now)`, `accept(now)` and `reject(now)` keep their signatures. `now` now comes from the
  injected `Clock` instead of `Instant.now()` in the service.
- `DriverProfile.startTrip()` is unchanged.

### User: one mapping change

`createdAt` loses its `LocalDateTime.now()` initializer and becomes `insertable = false, updatable = false`. The
database default `NOW()` sets it, and the application never inserts users.

### Session: unchanged

`createdAt` already comes from the injected `Clock` in `AuthService`. `tokenHash` is now computed by the
`TokenHasher` port instead of the static `GeneralHasher.sha256Hex`; the value is the same.

## Value objects

### CreateTripCommand: moved to `core.valueobject`, changed

| Component | Type | Validation (compact constructor → `InvalidTripException`) |
|-----------|------|-----------------------------------------------------------|
| `customerId` | `long` | must be > 0 |
| `serviceTypeName` | `String` | must be non-blank (existence is checked by the service against `ServiceTypeRepository`) |
| `pickup` | `Location` | non-null |
| `destination` | `Location` | non-null |
| `paymentMethod` | `PaymentMethod` | non-null |

Compared with today:
- `idempotencyKey` is **removed**. It was random per request and never read (research R8).
- `serviceTypeId: ServiceType` (enum) is replaced by `serviceTypeName: String`.
- `distanceMeters()` is **removed**, because the distance is now computed by `Trip.request`.

The HTTP layer already enforces the non-null, non-blank and coordinate-range checks with Bean Validation, so these
constructor checks are a second line of defense, not a new visible behavior.

### Event and TripEvent: annotation removed

- `TripEvent.aggregateId()` stays as a `default` method returning `tripId().toString()`, but **without**
  `@JsonProperty`. The Kafka adapter adds the property through a Jackson mix-in, so the JSON shape is decided in the
  adapter. See [contracts/events.md](./contracts/events.md).
- `eventId` and `occurredAt` come from `IdGenerator` and `Clock`.

### FareCalculator: moved to `core.domainservice`

`new FareCalculator(long perMeter, long minimum)`. `calculateFare(int distanceMeters)` returns
`max(distanceMeters × perMeter, minimum)` and throws `IllegalArgumentException` for distances ≤ 0 (unchanged). It is
built from `FareProperties` in `config.ApplicationConfig`.

## Application-service flows after the refactor

### TripService.create(CreateTripCommand)

```text
serviceTypes.findActiveByName(cmd.serviceTypeName) ── empty ──► InvalidTripException("Unknown service type, expected MyRide or MyCar")
trips.findActiveByCustomerId(cmd.customerId)        ── present ► ActiveTripExistsException(customerId, activeTripId)
now  = clock.instant()
trip = Trip.request(ids.newId(), cmd.customerId, serviceType, cmd.pickup, cmd.destination, fares, now)
trip = trips.add(trip)                               ── unique index ► ActiveTripExistsException(customerId, null)  [translated in adapter]
events.publish(new TripEvent.Requested(ids.newId(), now, trip.id, trip.customerId, trip.serviceTypeId, cmd.pickup.coordinate))
return trip
```

### OfferService.accept(tripId, driverUserId)

```text
now    = clock.instant()
driver = drivers.findByUserId(driverUserId)          ── empty ► DriverNotFoundException
offer  = offers.findPending(tripId, driver.id) filtered by !isExpired(now) ── empty ► OfferNotAvailableException
trip   = trips.findById(tripId)                      ── empty ► TripNotFoundException
trip.assignDriver(driver, now); trips.update(trip)
driver.startTrip();             drivers.update(driver)
offer.accept(now);              offers.update(offer)
events.publish(new TripEvent.DriverAssigned(ids.newId(), now, trip.id, driver.id))   [delivered after commit]
return trip
```

### OfferService.reject(tripId, driverUserId)

The flow is unchanged except that `now` comes from `clock` and the event id from `ids`. The `Rejected` event is now
delivered after commit.

All three flows run inside Spring's `@Transactional` on the service method.

## Validation and error mapping (unchanged at HTTP)

| Domain exception | Raised by | HTTP |
|------------------|-----------|------|
| `InvalidTripException` | `CreateTripCommand`, `TripService` (unknown type), `Trip.request` (same place) | 400, title "Invalid trip" |
| `ActiveTripExistsException` | `TripService` (pre-check), `JpaTripRepository.add` (concurrent insert) | 409, title "Active trip exists", `activeTripId` when known |
| `IllegalStateException` | `Trip.assignDriver` | 409 |
| `InvalidCredentialsException` | `AuthService` | 401 |
| `UserNotFoundException` | `HelloService` | 404 |
| `UnidentifiedCallerException` (**new**, `core.exception`) | `api.security.AuthenticatedCaller` | 401, title "Unauthenticated", `WWW-Authenticate: Bearer` |
| `DriverNotFoundException`, `OfferNotAvailableException`, `TripNotFoundException` | `OfferService` | unchanged: no specific handler today, so they fall through to the framework default |
