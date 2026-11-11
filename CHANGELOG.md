# Changelog

All notable changes to this project are recorded here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

No version has been released or tagged yet, so entries are grouped by date. Add new changes under
**Unreleased**; when a version is tagged, rename that section to the version number and date.

## [Unreleased]

### Fixed

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
