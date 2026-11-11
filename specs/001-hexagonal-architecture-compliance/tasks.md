---

description: "Task list for Hexagonal Architecture Compliance"
---

# Tasks: Hexagonal Architecture Compliance

**Input**: Design documents from `specs/001-hexagonal-architecture-compliance/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Test tasks are MANDATORY (constitution Principles II and III). Every user story includes unit tests for
its domain logic and, where it touches persistence, HTTP or events, integration tests. The two bug fixes (offer
events sent before commit, and a missing or non-numeric JWT `sub`) start with a failing regression test.

**Organization**: Tasks are grouped by user story, so each story lands as its own PR with `./mvnw test` green.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: The user story the task belongs to (US1–US4)
- Paths are relative to the repository root. Production code is in `src/main/java/org/kore/myapp/` and tests are in
  `src/test/java/org/kore/myapp/`.

## Ground rules for every task

- **Contract preservation.** Never change an HTTP path, a request or response field, a status code, a Problem Details
  title or detail, a Kafka topic, a key or an event JSON field, unless a task says so. The source of truth is
  [contracts/http-api.md](./contracts/http-api.md) and [contracts/events.md](./contracts/events.md).
- **Migrations.** Never edit a file in `src/main/resources/db/migration/`. This feature adds no migration.
- **Frozen violations store.** From T017 on, the store under `src/test/resources/archunit_store/` may only shrink.
  After each phase, run `./mvnw test`, commit the store changes ArchUnit made, and confirm the diff only deletes
  lines.
- **Moving an assertion.** When a test moves, keep its assertions; they can be reworded, but never weakened
  (research R12).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the test-only dependencies and make the integration tests run in `./mvnw test`.

- [X] T001 Add a `<archunit.version>` property (the latest 1.4.x) and a test-scoped dependency
  `com.tngtech.archunit:archunit-junit5:${archunit.version}` in `pom.xml`. Spring Boot doesn't manage ArchUnit, so
  the version must be pinned.
- [X] T002 Add test-scoped dependencies to `pom.xml`, without versions (they are managed by the Spring Boot 4.1 BOM):
  - `org.springframework.boot:spring-boot-testcontainers`
  - `org.testcontainers:testcontainers-junit-jupiter`
  - `org.testcontainers:testcontainers-postgresql`
  - `org.testcontainers:testcontainers-kafka`

  Run `./mvnw -q dependency:resolve` to confirm they resolve. If an artifact id differs in the managed Testcontainers
  version, use the one the BOM declares (check with `./mvnw help:effective-pom`).
- [X] T003 Configure `maven-surefire-plugin` in `pom.xml` with `<includes>` for `**/*Test.java`, `**/*Tests.java`
  and `**/*IT.java`, so integration tests run in the normal `test` phase (research R11). Don't add Failsafe.
- [X] T004 Confirm a container runtime is reachable (`docker info`). If it isn't, stop and tell the developer that
  Docker Desktop, Colima or Podman is required (spec Assumptions; quickstart Prerequisites). Then run `./mvnw test`
  and record the baseline result in the PR description. Today `MyAppTests` passes only if a local Postgres is
  running.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared test infrastructure, plus the golden event payloads. The payloads **must** be captured before any
production code changes.

**⚠️ CRITICAL**: No user story work starts until this phase is complete. T007 has to run against today's
`TripEvent`, before US3 removes `@JsonProperty`.

- [X] T005 Create `src/test/java/org/kore/myapp/TestcontainersConfiguration.java` as a
  `@TestConfiguration(proxyBeanMethods = false)` with two `@Bean @ServiceConnection` methods:
  - `PostgreSQLContainer` on image `postgres:16`
  - Testcontainers' `org.testcontainers.kafka.KafkaContainer` on image `apache/kafka:3.9.0`
- [X] T006 Update `src/test/java/org/kore/myapp/MyAppTests.java`:
  - add `@Import(TestcontainersConfiguration.class)`;
  - rename the test to `contextLoads()`;
  - remove the `System.out.println`.

  The test must pass with no hand-started Postgres. From this point `./mvnw test` needs a container runtime, so
  document that in **this** change (Principle V):
  - in `README.md`, add a "Running the tests" subsection under "Running locally". It covers the container runtime
    prerequisite (Docker Desktop, Colima or Podman) and `./mvnw test`, and says no hand-started Postgres or Kafka is
    needed for tests;
  - under **Unreleased** in `CHANGELOG.md`, add **Added**: Testcontainers-based tests (PostgreSQL 16, Kafka 3.9);
    running the tests requires a container runtime.
- [X] T007 Create `src/test/java/org/kore/myapp/adapter/kafka/EventJsonContractTest.java`, a plain JUnit test with no
  Spring context:
  1. Build one instance of each `TripEvent` subtype (`Requested`, `DriverAssigned`, `Rejected`, `Completed`,
     `Cancelled`) with fixed values: `eventId` `00000000-0000-0000-0000-000000000001`, `occurredAt`
     `2026-11-11T08:00:00Z`, `tripId` `00000000-0000-0000-0000-0000000000aa`, `customerId` 42, `serviceTypeId` 1,
     pickup `(-6.1754, 106.8272)`, `driverId` 7, fare `Money(25000)`, `cancelledBy` `CUSTOMER`.
  2. Serialize each instance with a `tools.jackson.databind.json.JsonMapper` configured like the application's.
  3. Write the output **once** to `src/test/resources/events/{requested,driver-assigned,rejected,completed,cancelled}.json`.
  4. Assert, by JSON tree equality, that serialization equals each fixture.

  Commit the fixtures. From now on they are the event contract ([contracts/events.md](./contracts/events.md)).
- [X] T008 [P] Create `src/test/java/org/kore/myapp/service/support/RecordingEventPublisher.java`. It implements
  `core.port.EventPublisher`, keeps the published events in a `List<Event>`, and exposes `events()` plus
  `single(Class<T>)`, which asserts exactly one event of that type and returns it.
- [X] T009 [P] Create `src/test/java/org/kore/myapp/service/support/Fixtures.java` with:
  - a fixed `Clock` (`2026-11-11T08:00:00Z`, UTC);
  - a sequential `IdGenerator` factory that returns `UUID`s `…0001`, `…0002`, and so on;
  - the locations `MONAS` (`-6.1754, 106.8272`) and `SENAYAN` (`-6.2183, 106.8022`), moved here from
    `TripServiceTest`.

  Until T037 creates the port, declare the generator's return type as `java.util.function.Supplier<UUID>`. T037
  switches it to `IdGenerator`.

**Checkpoint**: `./mvnw test` is green, `MyAppTests` runs on Testcontainers, and the event fixtures are committed.

---

## Phase 3: User Story 1 – Boundary violations are caught automatically (Priority: P1) 🎯 MVP

**Goal**: `./mvnw test` enforces architecture rules AR-01 to AR-09 ([contracts/architecture-rules.md](./contracts/architecture-rules.md)).
Today's violations are frozen in a store that can only shrink.

**Independent Test**: `./mvnw test -Dtest='ArchitectureTest,ArchitectureRulesSelfTest'` passes. Adding a forbidden
dependency (quickstart §2) makes `ArchitectureTest` fail and name both ends of the dependency.

### Tests for User Story 1 (MANDATORY) ⚠️

- [X] T010 [P] [US1] Create deliberately violating fixture classes **in test sources**, one per rule, so the rules
  can be proven to fire:
  - `src/test/java/org/kore/myapp/core/archfixture/CoreDependsOnService.java`: holds a field of type
    `org.kore.myapp.service.HelloService` (AR-01)
  - `CoreUsesSpring.java`: annotated `@org.springframework.stereotype.Component` (AR-03, AR-08)
  - `CoreCallsClock.java`: calls `Instant.now()` and `UUID.randomUUID()` (AR-07)
  - `src/test/java/org/kore/myapp/core/port/archfixture/PortWithDefaultMethod.java`: an interface with a default
    method and a static method (AR-05)
  - `PortWithJpa.java`: an interface extending `org.springframework.data.repository.Repository` (AR-04, AR-05)
  - `src/test/java/org/kore/myapp/adapter/archfixture/one/AdapterOne.java` and
    `src/test/java/org/kore/myapp/adapter/archfixture/two/AdapterTwo.java`: `AdapterOne` references `AdapterTwo`
    (AR-02)
  - `src/test/java/org/kore/myapp/api/archfixture/ApiUsesRepository.java`: holds a field of
    `core.port.TripRepository` (AR-09)
  - `src/test/java/org/kore/myapp/api/archfixture/ApiTransactional.java`: annotated
    `@org.springframework.transaction.annotation.Transactional` (AR-06)
- [X] T011 [US1] Create `src/test/java/org/kore/myapp/ArchitectureRulesSelfTest.java`. It imports **only** the T010
  fixture classes and their targets with `new ClassFileImporter().importClasses(...)`. For each rule constant from
  T012 it asserts two things:
  - `rule.evaluate(classes).hasViolation()` is true;
  - the failure report contains the fully qualified names of both the fixture class and the forbidden target
    (SC-002).

  It fails, by not compiling, until T012 exists.

### Implementation for User Story 1

- [X] T012 [US1] Create `src/test/java/org/kore/myapp/ArchitectureRules.java`. It is a final class with one
  `public static final ArchRule` per rule AR-01 to AR-09, written exactly as in
  [contracts/architecture-rules.md](./contracts/architecture-rules.md). Each rule gets a `.because("Principle …")`
  clause.
  - **AR-01**: `layeredArchitecture().consideringOnlyDependenciesInLayers()`, with layers `Core`, `Service`, `Api`,
    `Adapter`, `Config` and `Common`. `Common` is declared with `optionalLayer`.
  - **AR-02**: `slices().matching("org.kore.myapp.adapter.(*)..").should().notDependOnEachOther()`.
  - **AR-05**: a custom `ArchCondition<JavaClass>` that fails for an interface in `..core.port..` that has
    - a static method,
    - a non-abstract method (`JavaMethod.getModifiers()` lacking `ABSTRACT`), or
    - a raw supertype outside `org.kore.myapp..` and `java..`.
  - **AR-06**: two rules joined with `CompositeArchRule`:
    - classes or methods annotated with Spring `@Transactional` reside in `..service..`;
    - no class depends on `jakarta.transaction.Transactional`.
  - **AR-07**: `noClasses().that().resideInAnyPackage("..core..", "..service..").should().callMethod(...)` for
    `Instant.now()`, `LocalDateTime.now()`, `LocalDate.now()`, `Clock.systemUTC()`, `Clock.systemDefaultZone()` and
    `UUID.randomUUID()`.
  - **AR-09**: no class in `..api..` depends on `TripRepository`, `TripOfferRepository`,
    `DriverProfileRepository`, `UserRepository`, `SessionRepository`, `ServiceTypeRepository`, `EventPublisher`,
    `TokenIssuer` or `PasswordHasher`. Match by simple-name suffix, `Repository`, or an explicit name list, so that
    `TokenHasher` and `IdGenerator` are covered once they exist.

  T011 must now pass.
- [X] T013 [US1] Create `src/test/java/org/kore/myapp/ArchitectureTest.java` with
  `@AnalyzeClasses(packages = "org.kore.myapp", importOptions = ImportOption.DoNotIncludeTests.class)`. Add one
  `@ArchTest static final ArchRule` field per rule, each wrapping the T012 constant in `FreezingArchRule.freeze(...)`.
  `DoNotIncludeTests` keeps the T010 fixtures out of the production check.
- [X] T014 [US1] Create `src/test/resources/archunit.properties` with:

  ```properties
  freeze.store.default.path=src/test/resources/archunit_store
  freeze.store.default.allowStoreCreation=false
  freeze.refreeze=false
  ```
- [X] T015 [US1] Generate the initial store once, by running:

  ```bash
  ./mvnw test -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true
  ```

  Commit `src/test/resources/archunit_store/`.
- [X] T016 [US1] Review the generated store against the spec's Gaps table. Every frozen violation must map to one of
  G2–G13, and the PR description should list the count per rule.

  If a violation maps to no known gap, stop and report it. It is a gap the spec missed, and it needs a decision
  before it is frozen.
- [X] T017 [US1] Do the negative check from quickstart §2 by hand:
  1. Temporarily add a `HelloService` field to `core/entity/User.java`.
  2. Confirm `./mvnw test -Dtest=ArchitectureTest` fails and names both classes.
  3. Revert the change.
  4. Confirm the test is green again.

**Checkpoint**: US1 is shippable as PR 1, together with Phases 1–2. New violations now fail the build.

---

## Phase 4: User Story 2 – Business rules can be tested without infrastructure (Priority: P2)

**Goal**: The ports are plain and owned by the domain, implemented in `adapter/persistence`. Time and ids are
injected. `CreateTripCommand` lives in `core`. `Trip.request` owns the creation rules. "Service type" has one
definition. This covers G2, G3, G4, G8, G10 and G12 (research R2, R5, R6, R8, R9).

**Independent Test**: With the container runtime **stopped**,
`./mvnw test -Dtest='TripTest,CoordinateTest,CreateTripCommandTest,FareCalculatorTest,TripServiceTest,OfferServiceTest,AuthServiceTest'`
passes, and no Spring context starts (quickstart §3). With the runtime running, `./mvnw test -Dtest='Jpa*IT'` passes.

**Scope note**: In this phase `TripService` **keeps** its `DataIntegrityViolationException` catch and its
`ApplicationEventPublisher`, and the controllers keep their identity fallback. US3 removes them, and their store
entries stay frozen until then.

### Tests for User Story 2 (MANDATORY) ⚠️

Write these first. Most won't compile until the matching implementation task exists, which counts as failing.

- [X] T018 [P] [US2] Create `src/test/java/org/kore/myapp/core/valueobject/CreateTripCommandTest.java`. Each test
  asserts `InvalidTripException` for one case:
  - `customerId` ≤ 0
  - blank or null `serviceTypeName`
  - null `pickup`
  - null `destination`
  - null `paymentMethod`

  Add one test showing that a valid command is built and keeps its components.
- [X] T019 [P] [US2] Create `src/test/java/org/kore/myapp/core/domainservice/FareCalculatorTest.java` for
  `new FareCalculator(30, 10_000)`:
  - long distances pay `distance × perMeter`;
  - short distances pay the minimum;
  - a distance ≤ 0 throws `IllegalArgumentException`.
- [X] T020 [P] [US2] Extend `src/test/java/org/kore/myapp/core/entity/TripTest.java` with `Trip.request` cases:
  - status is `REQUESTED`, `requestedAt == now` and `id` is the given id;
  - `distanceMeters` equals the haversine distance between `MONAS` and `SENAYAN`, and `fare` comes from the
    calculator;
  - the minimum fare applies to a short trip;
  - the same pickup and destination throws `InvalidTripException` with message
    `Pickup and destination must be different places`;
  - `serviceTypeId` is the given `ServiceType`'s id.

  Keep every existing `assignDriver` assertion. Create trips with `Trip.request` instead of the public builder.
- [X] T021 [P] [US2] Rewrite `src/test/java/org/kore/myapp/service/TripServiceTest.java` against the new
  constructor, `TripService(TripRepository, ServiceTypeRepository, ApplicationEventPublisher, FareCalculator, Clock,
  IdGenerator)`. Use `Fixtures`, Mockito mocks of the plain ports, and a mocked `ApplicationEventPublisher` until
  US3.

  Keep these existing tests:
  - `createStoresDistanceAndFareFromCoordinates`
  - `createAppliesMinimumFareForShortTrips`
  - `createRejectsSamePickupAndDestination`
  - `createRejectsWhenCustomerHasUnfinishedTrip`
  - `createReportsActiveTripWhenConcurrentInsertHitsUniqueIndex` (stubs `add` to throw)
  - `createRethrowsOtherIntegrityViolations` (stubs `add` to throw)

  Change `createRejectsUnknownServiceType` so that `findActiveByName` returns empty and the test asserts
  `InvalidTripException` with message `Unknown service type, expected MyRide or MyCar`.

  Add these tests:
  - `createChecksServiceTypeBeforeActiveTrip`: an unknown type and an existing active trip together give the
    unknown-type error;
  - `createUsesInjectedClockAndIds`: the trip's `id` is `…0001` and `requestedAt` is the fixed instant. The published
    `Requested` event has `eventId` `…0002`, `occurredAt` equal to the fixed instant, and the trip's `customerId`,
    `serviceTypeId` and pickup coordinate.

  **Move** `activeTripLookupCoversEveryUnfinishedStatus` to T024. Don't delete it.
- [X] T022 [P] [US2] Create `src/test/java/org/kore/myapp/service/OfferServiceTest.java`, using `Fixtures`, mocks of
  the plain ports and `RecordingEventPublisher`.

  `accept` cases:
  - the happy path assigns the driver, sets the driver `ON_TRIP`, sets the offer to `ACCEPTED` with
    `respondedAt == now`, calls `update` on all three repositories, and publishes `DriverAssigned` with an id from
    the generator and `occurredAt == now`;
  - an unknown driver throws `DriverNotFoundException`;
  - no pending offer throws `OfferNotAvailableException`;
  - an offer whose `expiresAt` is before the fixed clock throws `OfferNotAvailableException`;
  - an offer exactly at `expiresAt` is accepted, because `isExpired` uses `isAfter`;
  - a missing trip throws `TripNotFoundException`;
  - a trip not in `REQUESTED` throws `IllegalStateException`.

  `reject` cases:
  - a pending offer becomes `REJECTED` and publishes `Rejected`;
  - no pending offer: nothing is published.
- [X] T023 [P] [US2] Create `src/test/java/org/kore/myapp/service/AuthServiceTest.java`:
  - a successful login stores a `Session` through `SessionRepository.add` with `tokenHash` equal to
    `TokenHasher.hash(accessToken)` (stub returns `"hashed"`), `createdAt` equal to the fixed clock, and
    `publicId`/`expiredAt` taken from the issued token;
  - an unknown username throws `InvalidCredentialsException`;
  - a wrong password throws `InvalidCredentialsException`, and no session is stored.
- [X] T024 [P] [US2] Create `src/test/java/org/kore/myapp/adapter/persistence/JpaTripRepositoryIT.java`:
  - Annotate it `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)`,
    `@Import({TestcontainersConfiguration.class, JpaTripRepository.class})`.
  - In `@BeforeEach`, insert a user row (`users`: name, username, password) with `JdbcTemplate`. Flyway already seeds
    the service types.

  Cases:
  - `add` then `findById` round-trips every column, and `version` becomes 0 after insert;
  - `update` changes the status and increments `version`;
  - `findActiveByCustomerId` finds the trip in each of `REQUESTED`, `DRIVER_ASSIGNED`, `DRIVER_ARRIVED` and `ON_TRIP`
    (this is the moved `activeTripLookupCoversEveryUnfinishedStatus`), and returns empty for `COMPLETED` and
    `CANCELLED`.

  Set statuses that need a driver with SQL, after inserting a `driver_profiles` row, because the
  `chk_trips_driver_required` check needs one.
- [X] T025 [P] [US2] Create `src/test/java/org/kore/myapp/adapter/persistence/JpaTripOfferRepositoryIT.java` (same
  slice as T024, importing `JpaTripOfferRepository`), with trip, driver and offers inserted by SQL:
  - `findPending(tripId, driverId)` returns the `PENDING` offer;
  - it returns empty for `ACCEPTED`, `REJECTED` and `EXPIRED` offers, and for another driver;
  - `update` persists `status` and `respondedAt`.
- [X] T026 [P] [US2] Create `src/test/java/org/kore/myapp/adapter/persistence/JpaDriverProfileRepositoryIT.java`:
  - `findByUserId` finds the profile, and returns empty for an unknown user;
  - `update` persists `availabilityStatus = ON_TRIP` and increments `version`.
- [X] T027 [P] [US2] Create `src/test/java/org/kore/myapp/adapter/persistence/JpaUserAndSessionRepositoryIT.java`
  (imports `JpaUserRepository` and `JpaSessionRepository`):
  - `findByUsername` finds an existing user and returns empty for an unknown one;
  - a user inserted by SQL without `created_at` gets the database default, and the entity reads it back;
  - `SessionRepository.add` persists every column.
- [X] T028 [P] [US2] Create `src/test/java/org/kore/myapp/adapter/persistence/JpaServiceTypeRepositoryIT.java`:
  - `findActiveByName("MyRide")` returns id 1 and `findActiveByName("MyCar")` returns id 2 (the seed data);
  - `"MYRIDE"`, `"myride"` and `"Bike"` return empty (case-sensitive);
  - after `UPDATE service_types SET active = false WHERE name = 'MyCar'`, `"MyCar"` returns empty.
- [X] T029 [P] [US2] Create `src/test/java/org/kore/myapp/adapter/security/Sha256TokenHasherTest.java`. It asserts
  that `hash("abc")` equals the known SHA-256 lowercase hex
  `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`, which is the same output as today's
  `GeneralHasher.sha256Hex`.

### Implementation for User Story 2

Work inside-out: domain → ports → adapters → services → config → inbound adapter.

- [X] T030 [P] [US2] Create `src/main/java/org/kore/myapp/core/port/IdGenerator.java`, an interface with one method,
  `UUID newId()`, and Javadoc explaining that it is the source of ids that appear in domain objects and events.
- [X] T031 [P] [US2] Create `src/main/java/org/kore/myapp/core/port/TokenHasher.java`, with
  `String hash(String token)` and Javadoc saying it is a one-way hash used to store an access token, as lowercase
  hex.
- [X] T032 [P] [US2] Create `src/main/java/org/kore/myapp/core/domainservice/FareCalculator.java` as a final class:
  - constructor `(long perMeter, long minimum)`;
  - same `calculateFare` logic as today;
  - no Spring annotations and no `FareProperties`.

  Delete `src/main/java/org/kore/myapp/common/FareCalculator.java`.
- [X] T033 [US2] Move `CreateTripCommand` from `src/main/java/org/kore/myapp/service/command/` to
  `src/main/java/org/kore/myapp/core/valueobject/CreateTripCommand.java`:
  - components: `(long customerId, String serviceTypeName, Location pickup, Location destination, PaymentMethod paymentMethod)`;
  - its compact constructor throws `InvalidTripException` for the T018 cases;
  - remove `idempotencyKey` (research R8) and `distanceMeters()`;
  - delete the `service/command` package;
  - update the import in `core/port/TripUseCase.java`.
- [X] T034 [US2] Change `src/main/java/org/kore/myapp/core/entity/Trip.java`:
  - remove `@GeneratedValue` from `id` and `@Setter` from the class;
  - make the builder package-private (`@Builder(access = AccessLevel.PACKAGE)`);
  - make `version` a `Long` with no default;
  - remove the `Instant.now()` default from `requestedAt`;
  - add `public static Trip request(UUID id, long customerId, ServiceType serviceType, Location pickup, Location destination, FareCalculator fares, Instant now)`,
    implementing the rules in [data-model.md](./data-model.md) (Trip → Construction). `serviceType` here is the
    `core.entity.ServiceType` entity.
- [X] T035 [US2] Delete the enum `src/main/java/org/kore/myapp/core/valueobject/ServiceType.java`. Fix every
  reference (`CreateTripRequest`, `TripService` and tests) to use the service-type **name** or the
  `core.entity.ServiceType` entity (research R9).
- [X] T036 [P] [US2] In `src/main/java/org/kore/myapp/core/entity/User.java`:
  - remove the `LocalDateTime.now()` initializer;
  - map `createdAt` with `@Column(name = "created_at", nullable = false, insertable = false, updatable = false)`.
- [X] T037 [US2] Rewrite the six storage ports in `src/main/java/org/kore/myapp/core/port/`:
  - `TripRepository.java`, `TripOfferRepository.java`, `DriverProfileRepository.java`, `UserRepository.java`,
    `SessionRepository.java` and `ServiceTypeRepository.java` become plain interfaces with exactly the signatures and
    Javadoc `@throws` in [contracts/ports.md](./contracts/ports.md);
  - remove `extends JpaRepository`, `@Query`, the default method and every `org.springframework` import;
  - delete `GeneralHasher.java`;
  - switch `Fixtures` (T009) to return `IdGenerator`.
- [X] T038 [P] [US2] Create `src/main/java/org/kore/myapp/adapter/persistence/SpringDataTripRepository.java`
  (package-private, `extends JpaRepository<Trip, UUID>`, with
  `findFirstByCustomerIdAndStatusIn(long, Collection<TripStatus>)`).

  Create `src/main/java/org/kore/myapp/adapter/persistence/JpaTripRepository.java`:
  - `@Repository`, public, implements `TripRepository`;
  - `add` → `saveAndFlush`; `update` → `save`; `findById` delegates;
  - `findActiveByCustomerId` calls `findFirstByCustomerIdAndStatusIn(customerId, TripStatus.activeStatuses())`.

  No exception translation yet (US3).
- [X] T039 [P] [US2] Create `src/main/java/org/kore/myapp/adapter/persistence/SpringDataTripOfferRepository.java`
  (package-private). It carries the `@Query` for `findPending`, copied verbatim from today's port.

  Create `src/main/java/org/kore/myapp/adapter/persistence/JpaTripOfferRepository.java`, implementing
  `TripOfferRepository` (`findPending`, `update`).
- [X] T040 [P] [US2] Create `src/main/java/org/kore/myapp/adapter/persistence/SpringDataDriverProfileRepository.java`
  (package-private) and `src/main/java/org/kore/myapp/adapter/persistence/JpaDriverProfileRepository.java`,
  implementing `findByUserId` and `update`.
- [X] T041 [P] [US2] Create the package-private
  `src/main/java/org/kore/myapp/adapter/persistence/SpringDataUserRepository.java` and
  `SpringDataSessionRepository.java`, plus `JpaUserRepository.java` (implements `findByUsername`) and
  `JpaSessionRepository.java` (implements `add` with `save`).
- [X] T042 [P] [US2] Create `src/main/java/org/kore/myapp/adapter/persistence/SpringDataServiceTypeRepository.java`
  (package-private, with `Optional<ServiceType> findByNameAndActiveTrue(String name)`) and
  `src/main/java/org/kore/myapp/adapter/persistence/JpaServiceTypeRepository.java` (implements `findActiveByName`).
- [X] T043 [P] [US2] Create `src/main/java/org/kore/myapp/adapter/security/Sha256TokenHasher.java`, a `@Component`
  implementing `TokenHasher` with the SHA-256/HexFormat logic from the deleted `GeneralHasher`. Wrap
  `NoSuchAlgorithmException` in `IllegalStateException`, as today.
- [X] T044 [US2] Ensure Spring Data scans the new package. If the `@SpringBootApplication` class doesn't already cover
  `org.kore.myapp.adapter.persistence` (it does, by default package scanning), skip this. Otherwise add
  `@EnableJpaRepositories(basePackages = "org.kore.myapp.adapter.persistence")` to a new
  `src/main/java/org/kore/myapp/config/PersistenceConfig.java`.

  Confirm that no `JpaRepository` remains under `core`:

  ```bash
  grep -r JpaRepository src/main/java/org/kore/myapp/core
  ```
- [X] T045 [US2] Create `src/main/java/org/kore/myapp/config/ApplicationConfig.java` (`@Configuration`):
  - `@Bean Clock clock()` returns `Clock.systemUTC()` (moved out of `SecurityConfig`);
  - `@Bean IdGenerator idGenerator()` returns `UUID::randomUUID`;
  - `@Bean FareCalculator fareCalculator(FareProperties p)` returns `new FareCalculator(p.perMeter(), p.minimum())`.

  Remove the `Clock` bean from `src/main/java/org/kore/myapp/config/SecurityConfig.java`.
- [X] T046 [US2] Rewrite `TripService.create` in `src/main/java/org/kore/myapp/service/TripService.java` to follow
  the TripService flow in [data-model.md](./data-model.md):
  - inject `ServiceTypeRepository`, `Clock`, `IdGenerator` and `core.domainservice.FareCalculator`;
  - keep the existing `insert`/`violates` catch around `trips.add(...)` for now (moved in US3);
  - keep `ApplicationEventPublisher` for now (replaced in US3);
  - build the `Requested` event from `ids.newId()` and `now`.

  T021 must pass.
- [X] T047 [US2] Update `src/main/java/org/kore/myapp/service/OfferService.java`:
  - inject `Clock` and `IdGenerator`;
  - replace both `Instant.now()` calls with `clock.instant()` and both `UUID.randomUUID()` calls with
    `ids.newId()`;
  - call `update(...)` instead of `save(...)`.

  T022 must pass, except for the after-commit behavior, which belongs to US3.
- [X] T048 [US2] Update `src/main/java/org/kore/myapp/service/AuthService.java` to inject `TokenHasher`, use
  `tokenHasher.hash(token.getAccessToken())` and call `sessionRepository.add(session)`. T023 must pass.
- [X] T049 [US2] Update the inbound adapter for the new command:
  - `src/main/java/org/kore/myapp/api/request/CreateTripRequest.java`: remove `serviceTypeId()`, `MY_RIDE` and
    `MY_CAR`;
  - `src/main/java/org/kore/myapp/api/controller/TripController.java`: build `CreateTripCommand` with
    `request.serviceType()` (the name) and stop calling `UUID.randomUUID()`. Leave the `jwt.getSubject()` fallback
    for US3.
- [X] T050 [US2] Run `./mvnw test`. Everything must be green: T018–T029, the rewritten unit tests, `MyAppTests` and
  `ArchitectureTest`.
  - Commit the store changes ArchUnit made. Expect removed entries for G2, G3, G4, G8, G10 and G12.
  - Confirm the store diff only removes lines.
  - Do quickstart §3: with the container runtime stopped, the listed unit tests pass.

**Checkpoint**: US2 is shippable as PR 2. Business rules are tested with no infrastructure (SC-004), and the storage
adapters are proven against PostgreSQL.

---

## Phase 5: User Story 3 – Infrastructure details stay in adapters (Priority: P3)

**Goal**:
- storage errors are translated in the adapter;
- one after-commit event adapter (which fixes offer events);
- the JSON shape is decided in the adapter;
- thin controllers, with 401 for an unusable identity;
- adapters grouped by technology;
- Spring `@Transactional` everywhere.

This covers G5, G6, G7, G9, G11 and G13 (research R3, R4, R7, R10).

**Independent Test**:
- `./mvnw test -Dtest='Jpa*IT,AfterCommitEventPublisherIT,AfterCommitEventPublisherTest,TripControllerTest,DriverOfferControllerTest,AuthenticatedCallerTest'`
  passes (quickstart §4–5);
- `ArchitectureTest` passes with an **empty** store;
- `grep -rE "dao\.|hibernate\.exception|ApplicationEventPublisher" src/main/java/org/kore/myapp/service` finds
  nothing.

**Depends on**: US2 (the persistence adapters, `Clock` and `IdGenerator` must exist).

### Tests for User Story 3 (MANDATORY) ⚠️

Regression tests for the two bugs come first and must **fail** before T061 and T066.

- [X] T051 [P] [US3] Create `src/test/java/org/kore/myapp/adapter/kafka/AfterCommitEventPublisherIT.java`:
  - `@SpringBootTest` with `@Import(TestcontainersConfiguration.class)`. Autowire the `EventPublisher` **port**,
    whichever bean implements it, plus `TransactionTemplate`.
  - Use a plain `org.apache.kafka.clients.consumer.KafkaConsumer<String, String>` on the container's bootstrap
    servers, subscribed to `trip-events` with a unique group id per test.

  Cases:
  - **rollback regression** (this fails today, because `KafkaPublisher` sends immediately): publish
    `DriverAssigned`, then `Rejected`, inside `transactionTemplate.execute(...)` and call `setRollbackOnly()`. Polling
    for 2 s returns no record for either trip id.
  - **commit**: publish each of the five event types inside a committed transaction. For each, exactly one record
    arrives with topic `trip-events`, key equal to `tripId.toString()`, and a value equal (as a JSON tree) to its
    fixture from T007.
  - **no early delivery**: inside a transaction, publish, then poll for 1 s **before** commit and get no record. After
    commit, the record arrives.

  The broker-down case is unit-tested in T052 instead, because stopping the shared container would break the other
  integration tests. Note this in [contracts/events.md](./contracts/events.md) under "Verified by".
- [X] T052 [P] [US3] Create `src/test/java/org/kore/myapp/adapter/kafka/AfterCommitEventPublisherTest.java`, a plain
  unit test with a mocked `KafkaEventSender` (`TransactionSynchronizationManager.initSynchronization()` in setup,
  `clearSynchronization()` in teardown). Cases:
  - `publish` inside the synchronization doesn't call the sender until the registered synchronizations'
    `afterCommit()` runs;
  - `afterCompletion(STATUS_ROLLED_BACK)` without `afterCommit` never sends;
  - a sender throwing `EventPublishException` in `afterCommit` is logged and not rethrown;
  - with no active synchronization, `publish` sends immediately and propagates `EventPublishException`.
- [X] T053 [P] [US3] Extend `src/test/java/org/kore/myapp/adapter/kafka/EventJsonContractTest.java` so that, besides
  the plain serialization, it serializes each event with the `JsonMapper` that `KafkaEventSender` builds (mapper plus
  `EventJsonMixin`) and asserts equality with the same fixtures.
- [X] T054 [P] [US3] Extend `src/test/java/org/kore/myapp/adapter/persistence/JpaTripRepositoryIT.java`:
  - **concurrent duplicate**: the test method uses `@Transactional(propagation = NOT_SUPPORTED)` and cleans up its
    rows afterwards. Two threads start together on a `CountDownLatch`, and each calls `trips.add(...)` for the same
    customer inside its own `TransactionTemplate`. Exactly one succeeds, and the other throws
    `ActiveTripExistsException` with `activeTripId == null`.
  - **other integrity violation**: `add` of a trip with `serviceTypeId = 999` throws a
    `DataIntegrityViolationException` that is **not** an `ActiveTripExistsException` (untranslated, research R3).
- [X] T055 [US3] In `src/test/java/org/kore/myapp/service/TripServiceTest.java`:
  - **move** `createReportsActiveTripWhenConcurrentInsertHitsUniqueIndex` and `createRethrowsOtherIntegrityViolations`
    out; T054 covers them against the real index;
  - add `createPropagatesActiveTripExistsFromRepository`: stub `add` to throw `ActiveTripExistsException`, and assert
    the same instance reaches the caller and no event is published;
  - switch the event assertions to `RecordingEventPublisher`.
- [X] T056 [P] [US3] Create `src/test/java/org/kore/myapp/api/security/AuthenticatedCallerTest.java`:
  - a numeric `sub` returns the id;
  - a missing `sub` throws `UnidentifiedCallerException`;
  - a non-numeric `sub` (`"abc"`) throws `UnidentifiedCallerException`.
- [X] T057 [P] [US3] Create `src/test/java/org/kore/myapp/api/controller/TripControllerTest.java` as
  `@WebMvcTest(TripController.class)`. Import `SecurityConfig`, `JsonAuthenticationEntryPoint`,
  `ProblemDetailWriter` and the exception handlers, `@MockitoBean TripUseCase` and `@MockitoBean JwtDecoder`, and use
  `SecurityMockMvcRequestPostProcessors.jwt()`.

  Cases, per [contracts/http-api.md](./contracts/http-api.md):
  - `201` with a `Location: /trips/{id}` header, and a body with `id`, `status` and `serviceType: 1`;
  - Bean Validation `400`;
  - `InvalidTripException` → `400` with title `Invalid trip` and the message as detail;
  - `ActiveTripExistsException` → `409` with title `Active trip exists` and `activeTripId` when it isn't null;
  - no token → `401` with title `Unauthenticated`.

  **Regression cases** (these fail today): a JWT without `sub`, and a JWT with `sub = "abc"`, each give `401` with
  title `Unauthenticated` and header `WWW-Authenticate: Bearer`. In both cases `TripUseCase` is never called.
- [X] T058 [P] [US3] Create `src/test/java/org/kore/myapp/api/controller/DriverOfferControllerTest.java`. Use the
  same setup as T057, with `@MockitoBean TripOfferUseCase`. Cases:
  - `200` with a `TripResponse` body;
  - `IllegalStateException` from the use case → `409`;
  - **regression**: missing or non-numeric `sub` → `401` as in T057.

### Implementation for User Story 3

- [X] T059 [P] [US3] Create `src/main/java/org/kore/myapp/adapter/kafka/TripEventsProperties.java`, a record
  annotated `@ConfigurationProperties(prefix = "app.kafka")` with `String tripEventsTopic` and
  `Duration publishTimeout`. The property names in `application.properties` don't change.
- [X] T060 [P] [US3] Create `src/main/java/org/kore/myapp/adapter/kafka/EventJsonMixin.java`, a package-private
  abstract class with `@JsonProperty("aggregateId") abstract String aggregateId();`. Use the same `@JsonProperty`
  annotation package that `TripEvent` imports today.
- [X] T061 [US3] Create `src/main/java/org/kore/myapp/adapter/kafka/KafkaEventSender.java`, a package-private
  `@Component`:
  - its constructor takes `KafkaTemplate<String, String>`, the application `JsonMapper` and `TripEventsProperties`,
    and builds a private mapper with `mapper.rebuild().addMixIn(Event.class, EventJsonMixin.class).build()`;
  - `send(Event)` contains today's `KafkaPublisher.publish` logic, wrapping failures in `EventPublishException`.

  If T053 shows the mix-in isn't applied, use the research R4 fallback: `valueToTree(event)` plus
  `put("aggregateId", event.aggregateId())`.

  Then create `src/main/java/org/kore/myapp/adapter/kafka/AfterCommitEventPublisher.java`, a public `@Component`
  implementing `EventPublisher`:
  - if `TransactionSynchronizationManager.isSynchronizationActive()`, register a `TransactionSynchronization` whose
    `afterCommit()` calls `sender.send(event)` inside a try/catch that logs `EventPublishException` at `error` with
    the event type and aggregate id;
  - otherwise call `sender.send(event)` directly.

  Delete `src/main/java/org/kore/myapp/adapter/KafkaPublisher.java`. Add `TripEventsProperties.class` to
  `@EnableConfigurationProperties` in `config`.

  T051, T052 and T053 must now pass.

  **Do T063 in the same commit.** Until `TripEventRelay` is gone, it calls the new publisher from an `AFTER_COMMIT`
  listener. A synchronization registered at that point never runs, so `Requested` events would be silently dropped
  in between.
- [X] T062 [US3] Remove the `@JsonProperty("aggregateId")` annotation and its import from
  `src/main/java/org/kore/myapp/core/valueobject/TripEvent.java`. Keep the default `aggregateId()` method; it is part
  of `Event`, not a port. Re-run T053 and T051.
- [X] T063 [US3] Update `src/main/java/org/kore/myapp/service/TripService.java`:
  - replace `ApplicationEventPublisher` with the `EventPublisher` port;
  - delete `ACTIVE_TRIP_CONSTRAINT`, `insert(...)` and `violates(...)`, and every `org.springframework.dao` and
    `org.hibernate` import;
  - replace `jakarta.transaction.Transactional` with `org.springframework.transaction.annotation.Transactional`.

  Delete `src/main/java/org/kore/myapp/core/event/TripEventRelay.java` and the `core/event` package.

  This change alters the event delivery contract, so document it in **this** change (Principles III and V):
  - in `README.md`, update the **Booking flow** section to say that `DriverAssigned` and `Rejected` are, like
    `Requested`, published only after the transaction commits, and are never published for a rolled-back change;
  - under **Unreleased** in `CHANGELOG.md`, add **Changed**: offer events (`DriverAssigned`, `Rejected`) are now
    published only after the transaction commits (previously they could be sent for a change that was later rolled
    back).
- [X] T064 [US3] Move the constraint translation into
  `src/main/java/org/kore/myapp/adapter/persistence/JpaTripRepository.java`:
  - add `static final String ACTIVE_TRIP_CONSTRAINT = "uq_trips_customer_active"` with today's Javadoc reference to
    migration V10;
  - `add` catches `DataIntegrityViolationException`. When the cause chain holds a Hibernate
    `ConstraintViolationException` for that constraint name (case-insensitive), it throws
    `new ActiveTripExistsException(trip.getCustomerId(), null)`; otherwise it rethrows.

  Update the port Javadoc in `core/port/TripRepository.java` to match. T054 and T055 must pass.
- [X] T065 [P] [US3] Create these two classes:
  - `src/main/java/org/kore/myapp/core/exception/UnidentifiedCallerException.java`: a domain exception that extends
    `RuntimeException`, with Javadoc "the request can't be attributed to a known user". Principle I requires
    failures to be domain exceptions in `core/exception` (research R7).
  - `src/main/java/org/kore/myapp/api/security/AuthenticatedCaller.java`: a final class with
    `static long userId(Jwt jwt)` that parses `sub` and throws `UnidentifiedCallerException` when `sub` is null or
    not a number.

  T056 must pass.
- [X] T066 [US3] Wire the new identity handling into the HTTP layer:
  - In `src/main/java/org/kore/myapp/api/handler/GlobalExceptionHandler.java`, add a handler for
    `UnidentifiedCallerException`. It returns `ResponseEntity<ProblemDetail>` with status `401`, title
    `Unauthenticated`, detail `Token is missing, invalid, or expired`, and header `WWW-Authenticate: Bearer`.
  - In `TripController.java` and `DriverOfferController.java` under `src/main/java/org/kore/myapp/api/controller/`,
    replace the `jwt.getSubject()` fallback and the `assert` with `AuthenticatedCaller.userId(jwt)`.

  T057 and T058 must pass.

  This changes a public HTTP contract, so document it in **this** change (Principles III and V):
  - in the API section of `README.md`, note that `POST /trips` and `POST /driver/offers/{tripId}/accept` return `401`
    Problem Details when the token's `sub` is missing or not numeric;
  - under **Unreleased** in `CHANGELOG.md`, add **Fixed**: a JWT without a numeric `sub` now gets `401` instead of
    `500` (or a trip for customer `0`).
- [X] T067 [P] [US3] Move `src/main/java/org/kore/myapp/common/ProblemDetailWriter.java` to
  `src/main/java/org/kore/myapp/api/handler/ProblemDetailWriter.java`, and
  `src/main/java/org/kore/myapp/adapter/JsonAuthenticationEntryPoint.java` to
  `src/main/java/org/kore/myapp/api/handler/JsonAuthenticationEntryPoint.java`. Delete the now-empty `common`
  package and update the imports in `config/SecurityConfig.java`.
- [X] T068 [P] [US3] Move the security adapters to `src/main/java/org/kore/myapp/adapter/security/`:
  - `JwtTokenIssuer.java`, keeping it package-private;
  - `JwtProperties.java`;
  - `BcryptHasher.java`, renamed to `BcryptPasswordHasher.java`.

  Update the imports in `config/SecurityConfig.java`. Afterwards `src/main/java/org/kore/myapp/adapter/` must contain
  only the subpackages `persistence`, `kafka` and `security`.
- [X] T069 [US3] Add `@Transactional(readOnly = true)` from Spring to `sayHello` in
  `src/main/java/org/kore/myapp/service/HelloService.java`. Confirm all four services use only
  `org.springframework.transaction.annotation.Transactional`.
- [X] T070 [US3] Run `./mvnw test`. Everything must be green. The frozen store must now be **empty**: every rule file
  in `src/test/resources/archunit_store/` has no violation lines. Commit the store.

  If an entry remains, fix the code. Don't edit the store by hand.

**Checkpoint**: US3 is shippable as PR 3. Offer events are delivered after commit, and the 401 fix is in. The
violations store is empty.

---

## Phase 6: User Story 4 – The code layout tells the architecture (Priority: P4)

**Goal**: The rules are permanent rather than frozen, and the documentation matches the code (FR-021, SC-001,
SC-007).

**Independent Test**:
- `src/test/resources/archunit_store/` and `archunit.properties` are gone, and `./mvnw test` is green;
- the README Architecture tree matches `find src/main/java/org/kore/myapp -type d`.

**Depends on**: US3, because the store must be empty.

### Tests for User Story 4 (MANDATORY) ⚠️

- [X] T071 [US4] In `src/test/java/org/kore/myapp/ArchitectureTest.java`, remove `FreezingArchRule.freeze(...)` from
  every rule so the rules run unfrozen. Delete `src/test/resources/archunit_store/` and
  `src/test/resources/archunit.properties`. Then `./mvnw test -Dtest='ArchitectureTest,ArchitectureRulesSelfTest'`
  must pass.

### Implementation for User Story 4

- [X] T072 [P] [US4] In the **Architecture** section of `README.md`, replace the tree with the post-refactor layout
  from [plan.md](./plan.md) (Source Code):
  - `core/event` and `common` are gone;
  - `core/domainservice`, `adapter/{persistence,kafka,security}` and `api/security` are new.

  Add one sentence per package role, taken from constitution Principle IV, and one sentence saying that
  `ArchitectureTest` enforces the rules.

  The test prerequisites (T006), the Booking flow (T063) and the API 401 note (T066) were already documented in the
  PRs that changed them; only check that they are still accurate.
- [X] T073 [P] [US4] Add these bullets under **Unreleased** in `CHANGELOG.md`:
  - **Changed**: the internal structure is now hexagonal (ports and adapters), enforced by an ArchUnit test.
  - **Removed**: the unused trip `idempotencyKey`.

  The Added, Changed and Fixed entries from T006, T063 and T066 are already there.
- [X] T074 [US4] Update the Sync Impact Report comment in `.specify/memory/constitution.md`:
  - mark `TODO(ARCH_TEST)`, `CORE_IMPORTS_SERVICE`, `FRAMEWORK_PORTS`, `PORT_LOGIC`, `INFRA_EXCEPTIONS`,
    `EVENT_PORT`, `CLOCK` and `COMMON_PACKAGE` **resolved by 001-hexagonal-architecture-compliance**;
  - mark `TODO(INTEGRATION_TEST_INFRA)` **partly resolved**: the infrastructure exists, and web-layer tests for
    `AuthController`/`HelloController` remain.

  Don't change any principle text in this task.
- [X] T075 [US4] Amend `.specify/memory/constitution.md` as a PATCH, from 2.0.0 to 2.0.1:
  - In the Principle IV role table, the `config` row says it holds application-wide `@ConfigurationProperties`, and
    the `adapter` row says adapter-specific properties records live in their adapter package and are enabled from
    `config`. This resolves the Principle IV (role table) Complexity Tracking row in [plan.md](./plan.md).
  - In Principle VIII, widen the mapping exception from "JPA mapping annotations" to "JPA mapping annotations and
    provider-specific mapping annotations (for example Hibernate's `@JdbcTypeCode`, `@CreationTimestamp`,
    `@UpdateTimestamp`), provided no business rule, response or event reads a value they generate". This resolves
    the Principle VIII Complexity Tracking row in [plan.md](./plan.md).
  - Add a Sync Impact Report entry for 2.0.1, and set **Last Amended** to the implementation date.

**Checkpoint**: US4 is shippable as PR 4. All of SC-001 to SC-008 are verifiable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final checks across the stories.

- [X] T076 Run `./mvnw -q clean compile -Dmaven.compiler.showWarnings=true`, and confirm the change introduces no new
  compiler warnings and leaves no unused imports in files it touched (Principle I).
- [X] T077 [P] Check SC-007 by hand. Pick 10 classes at random:

  ```bash
  find src/main/java -name '*.java' | sort -R | head -10
  ```

  For each one, confirm its package matches its role in the README Architecture section, and record the result in
  the PR 4 description.
- [X] T078 Run every step in [quickstart.md](./quickstart.md) (§1–§6), including the optional end-to-end smoke test
  (`POST /trips` → `201`/`409`/`400`, plus the `Requested` message on `trip-events`). Record the results in the PR 4
  description.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)** has no dependencies.
- **Foundational (Phase 2)** depends on Setup and blocks every story. T007 must run before any change to
  `TripEvent`.
- **US1 (Phase 3)** depends on Phase 2.
- **US2 (Phase 4)** depends on US1, because the frozen store must exist so the moves are checked and shrink it.
- **US3 (Phase 5)** depends on US2: it needs the persistence adapters, `Clock`/`IdGenerator`, and the plain
  `TripRepository`.
- **US4 (Phase 6)** depends on US3, because the store must be empty.
- **Polish (Phase 7)** depends on US4.

The stories are **sequential** by nature: this is a refactor of one codebase, and each story removes violations
that the previous one made visible.

### PR mapping

| PR | Tasks |
|----|-------|
| PR 1 | Phases 1–3 (T001–T017) |
| PR 2 | Phase 4 (T018–T050) |
| PR 3 | Phase 5 (T051–T070) |
| PR 4 | Phases 6–7 (T071–T078) |

This puts the Testcontainers setup in PR 1, earlier than the plan's slice table: T007's fixtures must be captured
before anything changes, and `MyAppTests` benefits immediately.

### Within each story

- Test tasks come first and fail, either by not compiling or by failing.
- Then domain → ports → outbound adapters → application services → config → inbound adapter.
- The checkpoint task (T017, T050, T070, T071) runs the full suite and commits the store before the PR.

## Parallel Opportunities

- **Phase 2**: T008 ∥ T009, after T005–T007.
- **US1**: T010 can be written alongside T012. T011, T013 and T014 follow T012.
- **US2 tests**: T018 ∥ T019 ∥ T020 ∥ T021 ∥ T022 ∥ T023 ∥ T024 ∥ T025 ∥ T026 ∥ T027 ∥ T028 ∥ T029, all in
  different files.
- **US2 implementation**: T030 ∥ T031 ∥ T032 ∥ T036. After T037, T038 ∥ T039 ∥ T040 ∥ T041 ∥ T042 ∥ T043.
- **US3 tests**: T051 ∥ T052 ∥ T053 ∥ T054 ∥ T056 ∥ T057 ∥ T058.
- **US3 implementation**: T059 ∥ T060 ∥ T065 ∥ T067 ∥ T068.
- **US4**: T072 ∥ T073.

### Parallel example: User Story 2

```bash
# Tests, all in separate files:
Task: "CreateTripCommandTest in src/test/java/org/kore/myapp/core/valueobject/CreateTripCommandTest.java"
Task: "FareCalculatorTest in src/test/java/org/kore/myapp/core/domainservice/FareCalculatorTest.java"
Task: "OfferServiceTest in src/test/java/org/kore/myapp/service/OfferServiceTest.java"
Task: "JpaServiceTypeRepositoryIT in src/test/java/org/kore/myapp/adapter/persistence/JpaServiceTypeRepositoryIT.java"

# Persistence adapters, after the ports are rewritten (T037):
Task: "JpaTripRepository + SpringDataTripRepository in src/main/java/org/kore/myapp/adapter/persistence/"
Task: "JpaTripOfferRepository + SpringDataTripOfferRepository in src/main/java/org/kore/myapp/adapter/persistence/"
Task: "JpaServiceTypeRepository + SpringDataServiceTypeRepository in src/main/java/org/kore/myapp/adapter/persistence/"
```

### Parallel example: User Story 3

```bash
Task: "AfterCommitEventPublisherIT (rollback regression) in src/test/java/org/kore/myapp/adapter/kafka/"
Task: "TripControllerTest (401 regression) in src/test/java/org/kore/myapp/api/controller/TripControllerTest.java"
Task: "DriverOfferControllerTest in src/test/java/org/kore/myapp/api/controller/DriverOfferControllerTest.java"
Task: "JpaTripRepositoryIT concurrent duplicate case in src/test/java/org/kore/myapp/adapter/persistence/"
```

---

## Implementation Strategy

### MVP first (User Story 1)

1. Phases 1–2: add the dependencies, Testcontainers, and the golden event fixtures.
2. Phase 3: add `ArchitectureTest` with today's violations frozen.
3. **Stop and validate**: the negative check fails as expected (T017), and the suite is green.
4. Ship PR 1. From here on, nobody can add a new boundary violation.

### Incremental delivery

1. **PR 2 (US2)**: ports, domain rules, clock and ids, persistence adapters with real Postgres tests. The store
   shrinks.
2. **PR 3 (US3)**: event adapter, exception translation, thin controllers, package grouping. The store is empty.
3. **PR 4 (US4)**: unfreeze, then docs, CHANGELOG and constitution.

Each PR leaves `./mvnw test` green, and the contracts in [contracts/](./contracts/) stay unchanged apart from the two
documented fixes.

---

## Notes

- **[P]** means a different file with no dependency on an incomplete task.
- Commit after each task or logical group. Each commit message says *why* (constitution Workflow §6).
- Reviewers block any PR whose diff **adds** a line to `archunit_store`.
- If a task reveals a design gap the plan doesn't cover, stop and record it in [research.md](./research.md) before
  improvising.

---

## Phase 8: Convergence

- [X] T079 CRITICAL: Record the new runtime dependency `org.springframework.boot:spring-boot-starter-flyway` and why it was needed (Spring Boot 4 moved Flyway auto-configuration into its own module, so migrations never ran on startup) in `specs/001-hexagonal-architecture-compliance/plan.md` (Technical Context, which says "No new runtime dependency is added", and the Constitution Check V bullet) and as a new decision in `research.md`, per Constitution (Technology & Quality Standards: "Adding a runtime dependency MUST be justified in the plan") (contradicts)
- [X] T080 CRITICAL: Add tests for the moved security adapters: `src/test/java/org/kore/myapp/adapter/security/JwtTokenIssuerTest.java` (real `NimbusJwtEncoder`/`NimbusJwtDecoder` round trip with the test key; `sub`, `iss`, `sid`, `roles` claims; `expiresAt` = fixed `Clock` + TTL; `TokenIssuanceException` when encoding fails) and `BcryptPasswordHasherTest.java` (hash then compare succeeds, wrong password fails), per Constitution VII ("every outbound adapter MUST have an integration test against the real technology") and plan Complexity Tracking row III, which claims these unit tests exist (missing)
- [X] T081 CRITICAL: Make `src/main/java/org/kore/myapp/core/valueobject/Token.java` immutable (record, or remove `@Setter`), updating `JwtTokenIssuer`, `AuthService`, `LoginResponse` and tests, per Constitution I ("Value objects and DTOs MUST be immutable") (contradicts)
- [X] T082 CRITICAL: Remove public class-level `@Setter` from `core/entity/DriverProfile.java`, `ServiceType.java` and `User.java`, so state changes only through domain methods; replace the setter calls in tests (for example `AuthServiceTest`) with builders or test fixtures, per Constitution I ("Mutable state is confined to entities and changed only through methods that enforce their invariants") (contradicts)
- [X] T083 CRITICAL: Add `@WebMvcTest`s for `AuthController` (`POST /auth/login`: 200 body, 400 validation, 401 invalid credentials) and `HelloController` (`GET /hello/{username}`: 200, 401 without token, 404 unknown user) in `src/test/java/org/kore/myapp/api/controller/`, closing the rest of `TODO(INTEGRATION_TEST_INFRA)`, per Constitution III ("Every HTTP endpoint MUST have a web-layer test"); currently recorded only as an accepted gap in plan Complexity Tracking (missing)
- [X] T084 Reconcile FR-007 with the implementation. `JpaTripRepository.add` deliberately lets non-active-trip `DataIntegrityViolationException`s cross the `TripRepository` port (research R3, and asserted by `JpaTripRepositoryIT.addRethrowsOtherIntegrityViolationsUntranslated`), but FR-007 in `spec.md` says ports MUST NOT let infrastructure errors cross. Either amend FR-007 through `/speckit-specify` to match R3, or translate unexpected failures into a domain exception and update the test, per FR-007 (contradicts)
- [X] T085 Run quickstart §3 with the container runtime stopped, and confirm the domain and service unit tests (`TripTest`, `CoordinateTest`, `CreateTripCommandTest`, `FareCalculatorTest`, `TripServiceTest`, `OfferServiceTest`, `AuthServiceTest`) pass with no Spring context. T050 required this, but it wasn't executed. Per SC-004 (partial)
- [X] T086 Run the optional end-to-end smoke test from quickstart §5 against **throwaway** PostgreSQL and Kafka containers (override `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `KAFKA_BOOTSTRAP_SERVERS` so the developer's own database is never migrated): `POST /trips` → 201 with `"serviceType": 1`, repeat → 409, `"MYRIDE"` → 400, and one `Requested` message on `trip-events`, per FR-019 / T078 (partial)
- [X] T087 Document the test-only deviations made during implementation in `research.md` (R11) and `quickstart.md`: the Kafka test image is `apache/kafka:3.9.1` (3.9.0 rejects the Testcontainers 2 advertised listener), `src/test/resources/config/application.properties` supplies a fake JWT key so tests never read `.env`, and the architecture-rule fixtures live in `adapter/fixtureone|fixturetwo` (not `adapter/archfixture/one|two`, which share one slice), per plan: R11 / T010 (unrequested)
