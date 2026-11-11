# Quickstart: Validating Hexagonal Architecture Compliance

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

This guide shows how to check that the feature works: the architecture guard, the framework-free unit tests, the
real-technology adapter tests, and unchanged API behavior. It is a run guide, not an implementation guide. For what
each test asserts, see the [contracts](./contracts/).

## Prerequisites

- JDK 17+
- A running container runtime (Docker Desktop, Colima or Podman with the Docker socket). It is **required** for
  `./mvnw test`, because the adapter integration tests start PostgreSQL 16 and Kafka 3.9 containers (research R11).
  Without one, those tests fail rather than skip.
- Nothing else. No hand-started Postgres or Kafka is needed for the test suite, and no `.env` either: the tests read
  test-only settings from `src/test/resources/config/application.properties` (research R11, implementation notes).

Check that a container runtime is available:

```bash
docker info --format '{{.ServerVersion}}'
```

## 1. Whole suite: the merge gate

```bash
./mvnw test
```

**Expected**:
- `BUILD SUCCESS`.
- The run includes `ArchitectureTest`, the unit tests, the `*IT` adapter tests and `MyAppTests`.
- At the end of the feature, `src/test/resources/archunit_store/` doesn't exist (SC-001).

## 2. Architecture guard only (US1)

```bash
./mvnw test -Dtest=ArchitectureTest
```

**Expected**: every rule AR-01 to AR-09 passes ([architecture-rules.md](./contracts/architecture-rules.md)).

**Negative check (SC-002)**: on a scratch branch, add `import org.kore.myapp.service.TripService;` and a field of
that type to any class in `core.entity`. Then run the command again.

**Expected**: the test fails, and the message names the `core.entity` class and `org.kore.myapp.service.TripService`.
Revert the change afterwards.

**During US1–US3 only**: the store under `src/test/resources/archunit_store/` must only shrink between commits:

```bash
git diff --stat -- src/test/resources/archunit_store/
```

## 3. Business rules without infrastructure (US2, SC-004)

Stop or disconnect the container runtime, then run:

```bash
./mvnw test -Dtest='TripTest,CoordinateTest,CreateTripCommandTest,FareCalculatorTest,TripServiceTest,OfferServiceTest,AuthServiceTest'
```

**Expected**:
- every listed test passes with no container runtime running;
- no Spring context is started (no Spring banner in the output);
- timestamps and ids in the assertions equal the fixed `Clock` and `IdGenerator` values.

## 4. Adapters against the real technology (US3, FR-022, SC-008)

Start the container runtime again, then run:

```bash
./mvnw test -Dtest='Jpa*IT,AfterCommitEventPublisherIT'
```

**Expected**: everything passes, including:
- `JpaTripRepositoryIT`: two concurrent `add`s for the same customer result in exactly one
  `ActiveTripExistsException`;
- `AfterCommitEventPublisherIT`: no message arrives for a rolled-back transaction, and each event type matches its
  golden JSON fixture ([events.md](./contracts/events.md)).

## 5. API behavior unchanged (FR-019)

Run the web-layer tests:

```bash
./mvnw test -Dtest='TripControllerTest,DriverOfferControllerTest'
```

**Expected**: everything passes, including the three 401 cases in [http-api.md](./contracts/http-api.md).

**Optional end-to-end smoke test**: start **throwaway** Postgres and Kafka containers and point the app at them with
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `KAFKA_BOOTSTRAP_SERVERS` environment variables, which override `.env`.
Since Flyway now migrates on startup (research R14), never aim this at a database you care about. Then:
1. Log in, and use the token to call `POST /trips` with `"serviceType": "MyRide"`. **Expected**: `201`, with a body
   containing `"serviceType": 1`.
2. Repeat the same request. **Expected**: `409` "Active trip exists".
3. Send `"serviceType": "MYRIDE"`. **Expected**: `400` "Unknown service type, expected MyRide or MyCar".
4. Consume `trip-events` (for example with `kafka-console-consumer.sh --topic trip-events --property print.key=true`).
   **Expected**: exactly one `Requested` message, keyed by the trip id, with the same JSON fields as before the
   change.

## 6. Documentation (US4, FR-021)

- The README's Architecture section tree matches `src/main/java/org/kore/myapp` (SC-007).
- `CHANGELOG.md` (Unreleased) mentions:
  - the after-commit delivery of offer events;
  - the 401 for an unusable caller identity;
  - the container runtime prerequisite for tests.
- The constitution's Sync Impact Report marks `ARCH_TEST`, `CORE_IMPORTS_SERVICE`, `FRAMEWORK_PORTS`, `PORT_LOGIC`,
  `INFRA_EXCEPTIONS`, `EVENT_PORT`, `CLOCK` and `COMMON_PACKAGE` resolved, and `INTEGRATION_TEST_INFRA` partly
  resolved.
