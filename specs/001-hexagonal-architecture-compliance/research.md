# Research: Hexagonal Architecture Compliance

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-11

Phase 0 resolves every open design question in the plan's Technical Context. Each entry gives the decision, why,
and what else was considered. Gap references (G1–G13) point to the spec's Gaps table.

## Additional findings from the code survey

The planning survey found three more issues the spec's Gaps table doesn't list. They fall under existing
requirements, so the spec doesn't need to change:

| Finding | Where | Covered by |
|---------|-------|------------|
| `TripEvent` carries a Jackson `@JsonProperty` annotation, so the JSON shape of an event is decided inside the domain. | `core/valueobject/TripEvent.java` | FR-011, VII ("transport details are decided in the adapter") |
| A trip stores its service type as `ServiceType.ordinal()` of an enum. This only works because the seed rows happen to get ids 1 and 2 in the same order as the enum constants. | `service/TripService.java` | FR-018 (G12) |
| `JwtProperties` lives in `adapter` but is used by `config`. `KafkaPublisher` reads settings with `@Value` instead of a typed properties record. | `adapter/` | FR-011, Principle V |

---

## R1. Automated architecture guard (G1, FR-001–FR-003)

**Decision**: Add **ArchUnit** (`com.tngtech.archunit:archunit-junit5`, the latest 1.4.x when implemented, pinned in
`pom.xml` because Spring Boot doesn't manage its version). Write one test class,
`src/test/java/org/kore/myapp/ArchitectureTest.java`, that defines:

1. A `layeredArchitecture()` with the six layers of Principle IV and these allowed accesses:
   - `Core`: accessed by `Service`, `Api`, `Adapter`, `Config`
   - `Service`: accessed by `Config` only
   - `Api`: accessed by `Config` only
   - `Adapter`: accessed by `Config` only
   - `Common`: accessed by any layer, and may access no other layer (declared optional, since it may end up empty)
2. `slices().matching("org.kore.myapp.adapter.(*)..").should().notDependOnEachOther()`, so adapter technologies
   don't call each other.
3. Rules for framework-free domain and ports:
   - Nothing in `core` depends on `org.springframework..`, `org.apache.kafka..`, `jakarta.servlet..`,
     `tools.jackson..` or `com.fasterxml.jackson..`.
   - Nothing in `core.port` or `core.valueobject` depends on `jakarta.persistence..` or `org.hibernate..`.
   - Mapping annotations are permitted only in `core.entity`, per Principle VIII.
4. Interfaces in `core.port` have no static methods and no default methods (FR-004). This is a custom `ArchCondition`
   over `JavaClass.getMethods()`.
5. `@Transactional`, from either Spring or `jakarta.transaction`, is used only by classes in `service`, and only the
   Spring annotation is allowed (FR-017).
6. No class in `core` or `service` calls `Instant.now()`, `LocalDateTime.now()`, `Clock.systemUTC()` or
   `UUID.randomUUID()` (FR-016).

The known-exception list (FR-002) uses ArchUnit's **`FreezingArchRule`**. Each rule is wrapped in `freeze(...)`, and
the violation store is committed under `src/test/resources/archunit_store/`. The settings in
`src/test/resources/archunit.properties` are:

```properties
freeze.store.default.allowStoreCreation=false
freeze.refreeze=false
```

With these settings, a new violation fails the build, and a fixed violation is removed from the store automatically,
so the list can only shrink. The final step of the feature deletes the store and drops `freeze(...)`, so a
violation can never be frozen again (SC-001).

**Rationale**: ArchUnit is what the constitution already names (Technology & Quality Standards). It runs as plain
JUnit inside `./mvnw test`, and its failure messages name both sides of a forbidden dependency (SC-002).
`FreezingArchRule` is ArchUnit's built-in way to keep a list of known violations that can only shrink.

**Alternatives considered**:
- *Hand-written `ignoreDependency(...)` exceptions*: rejected. The list doesn't shrink automatically, and it's easy to
  widen by accident.
- *Spring Modulith `ApplicationModules.verify()`*: rejected. It checks module boundaries, not hexagonal layers, and
  would add a second architectural vocabulary.
- *Splitting into Maven modules*: rejected. It would enforce the rule at compile time, but it restructures the build
  and goes beyond Principle V (simplicity) for a single-service learning project.

## R2. Framework-free storage ports and persistence adapters (G3, G4, FR-004, FR-005)

**Decision**: Each storage port in `core/port` becomes a plain interface that declares only the operations the
application calls. `adapter/persistence` implements each port with two classes:

- a package-private Spring Data interface named `SpringData<Entity>Repository`, which extends `JpaRepository` and is
  the only place derived queries and JPQL live;
- a public adapter class named `Jpa<Port>` (for example `JpaTripRepository implements TripRepository`). It is
  annotated `@Repository`, delegates to the Spring Data interface, and translates exceptions (see R3).

The port operations come from what the services call today:

| Port | Operations |
|------|------------|
| `TripRepository` | `Optional<Trip> findById(UUID)`, `Optional<Trip> findActiveByCustomerId(long)`, `Trip add(Trip)`, `Trip update(Trip)` |
| `TripOfferRepository` | `Optional<TripOffer> findPending(UUID tripId, long driverId)`, `TripOffer update(TripOffer)` |
| `DriverProfileRepository` | `Optional<DriverProfile> findByUserId(long)`, `DriverProfile update(DriverProfile)` |
| `UserRepository` | `Optional<User> findByUsername(String)` |
| `SessionRepository` | `Session add(Session)` |
| `ServiceTypeRepository` | `Optional<ServiceType> findActiveByName(String)` (see R9) |

`TripRepository.findActiveByCustomerId` used to be a `default` method. It is now a plain declaration, and the adapter
implements it with `findFirstByCustomerIdAndStatusIn(customerId, TripStatus.activeStatuses())`. The `@Query` on
`findPending` moves onto `SpringDataTripOfferRepository`.

`add` (insert) and `update` are separate operations for two reasons. `add` flushes immediately, so a unique-index
violation surfaces inside the adapter, where it can be translated (R3). And with ids now assigned by the domain (R5),
each call says plainly whether it means insert or update.

**Rationale**: this is the textbook ports-and-adapters split. The domain owns a small, intention-revealing contract,
and Spring Data becomes an implementation detail that can't leak `findAll`, `deleteAll` or paging into the core.
Unit tests mock six small interfaces instead of `JpaRepository`'s large surface.

**Alternatives considered**:
- *Keep the ports extending `JpaRepository` and document an exception*: rejected. It is the single largest
  violation of Principle VI and the main reason for this feature.
- *A separate persistence model (`TripJpaEntity`) with mappers*: rejected. Principle VIII explicitly permits mapping
  annotations on domain entities so that a duplicate model isn't needed, and the spec's assumptions keep them.

## R3. Translating infrastructure exceptions (G5, FR-007, FR-009)

**Decision**: `JpaTripRepository.add` calls `saveAndFlush` and catches `DataIntegrityViolationException`. If the cause
chain contains Hibernate's `ConstraintViolationException` with constraint name `uq_trips_customer_active`, it throws
`ActiveTripExistsException(customerId, null)`. Any other integrity violation is rethrown unchanged. The constraint
name and the cause-walking logic move from `TripService` to the adapter verbatim.

Persistence adapters translate only failures the application can act on. Today that is the active-trip conflict.
Unexpected infrastructure failures (connection loss, an optimistic-lock conflict found at commit, a foreign-key
violation) propagate unchanged and produce a 500, as they do today. Application services never catch them.

**Rationale**: an exception raised at **commit** (for example an optimistic-lock conflict on `update`) is thrown by the
transaction proxy around the service, after the adapter method has already returned, so no adapter can translate it.
Wrapping every adapter call in a generic `DataStoreException` would add code to every method and still miss
commit-time failures, without changing any observable behavior. This is a narrow, explicit deviation from the
literal "MUST NOT leak infrastructure exceptions" rule of Principle VI, recorded in the plan's Complexity Tracking.

**Alternatives considered**:
- *A generic `DataStoreException` wrapper in every adapter method*: rejected for the reasons above.
- *An AOP aspect that translates `DataAccessException` for all adapters*: rejected. It needs a new AspectJ
  dependency and has the same commit-time blind spot.
- *Flushing in every `update`, so commit-time conflicts surface in the adapter*: rejected. It changes transaction
  behavior, adds one database round-trip per write, and no requirement asks for it.

## R4. One event port, delivered after commit (G6, G7, FR-012, FR-013)

**Decision**: `EventPublisher.publish(Event)` remains the **only** outbound event port. Both application services call
it inside their transaction. Spring's `ApplicationEventPublisher` and `core/event/TripEventRelay` are removed.

In `adapter/kafka`:
- `AfterCommitEventPublisher implements EventPublisher`:
  - If a transaction synchronization is active, it registers a `TransactionSynchronization` whose `afterCommit()`
    sends the event, so a rollback discards it.
  - If no transaction is active, it sends immediately. No current caller does this, because every use case is
    transactional.
  - A send failure after commit is caught and logged, and never thrown (FR-013). This matches today's
    `TripEventRelay`.
- `KafkaEventSender` (package-private) does the actual send. It has the same topic, key (`aggregateId`), JSON value
  and timeout as today's `KafkaPublisher`.
- `EventJsonMixin` (package-private) is a Jackson mix-in that adds the `aggregateId` property to the serialized form
  of `Event`. The sender uses a copy of the application `ObjectMapper` with the mix-in registered, so
  `@JsonProperty` can be removed from the domain and the payload stays byte-for-byte the same (FR-019).
- `TripEventsProperties` (`@ConfigurationProperties("app.kafka")`, fields `tripEventsTopic` and `publishTimeout`)
  replaces the two `@Value` injections.

**Effect on offer events (FR-012)**: `OfferService` already calls `EventPublisher` inside the transaction. Because the
port implementation now waits for commit, offer events become after-commit without any change to `OfferService`.

**Rationale**: putting the "after commit" policy in the adapter matches Principle VIII ("when and how they are
delivered is decided by adapters"). It also gives both services the same mechanism, which closes G7. A
`TransactionSynchronization` is the most direct way to do this and doesn't need Spring's event bus as a middle step.

**Alternatives considered**:
- *Keep `ApplicationEventPublisher` and move the `@TransactionalEventListener` relay into `adapter/kafka`*: workable,
  but it uses two mechanisms (the Spring event bus plus a listener) where one is enough. Also, with
  `fallbackExecution=false`, events published outside a transaction are silently dropped.
- *Transactional outbox table*: rejected. It is the robust answer to "delivery may fail after commit", but no current
  requirement asks for guaranteed delivery, and it adds a table, a poller and new failure modes (Principle V).
  Logging a failure after commit stays the accepted behavior.

**Mix-in risk**: Jackson must apply a mix-in on the `Event` interface to the `aggregateId()` method that
`TripEvent` overrides as a default method. The FR-022 Kafka integration test asserts the exact JSON of every event
type, so a mismatch is caught immediately. If the mix-in doesn't apply, the fallback is for `KafkaEventSender` to
build the message as `objectMapper.valueToTree(event)` and add `aggregateId` explicitly. That stays inside the
adapter and produces the same JSON.

## R5. Clock and identifier source (G8, FR-016)

**Decision**:
- **Time**: services already have access to `java.time.Clock` (a JDK type, so it is allowed in ports and services).
  `TripService` and `OfferService` take a `Clock` and pass `clock.instant()` into domain methods. The `Clock` bean
  moves from `SecurityConfig` to a new `config/ApplicationConfig`.
- **Identifiers**: a new outbound port, `core/port/IdGenerator` (`UUID newId()`), supplies trip ids and event ids.
  `ApplicationConfig` provides the production bean as `UUID::randomUUID`. It's a one-line lambda in the composition
  root, which may use anything, so it doesn't need an adapter package.
- **Trip id**: `Trip.id` loses `@GeneratedValue` and is assigned by `Trip.request(...)` from `IdGenerator`, because the
  id appears in events. To keep Spring Data's insert-vs-update detection correct for an entity with an assigned id,
  `Trip.version` changes from `long = 0` to `Long` (null means new). The `version` column keeps its `NOT NULL
  DEFAULT 0`, and Hibernate sets it to 0 on insert, so no migration is needed.
- **`Trip.requestedAt`** loses its `Instant.now()` initializer and is set by `Trip.request(..., now)`.
- **`User.createdAt`** loses its `LocalDateTime.now()` initializer. The application never inserts users (the
  database default `NOW()` fills the column), so the field is mapped `insertable = false, updatable = false`.
- **Hibernate-specific mapping annotations stay, as a recorded exception.** These are `Trip.createdAt`/`updatedAt`
  (`@CreationTimestamp`/`@UpdateTimestamp`) and `Session.tokenHash` (`@JdbcTypeCode(SqlTypes.CHAR)`). Principle VIII
  permits only *JPA* mapping annotations, and `@CreationTimestamp` reads the system clock, so these are a deviation.
  It is recorded in the plan's Complexity Tracking, and T075 resolves it with a constitution PATCH.
  - The audit timestamps are read by no rule, response or event.
  - `@JdbcTypeCode` is needed so Hibernate's `ddl-auto=validate` accepts the `CHAR(64)` column.
  - Replacing the timestamps would need a new migration with an `updated_at` trigger, or clock-fed lifecycle code, for
    columns nothing reads.
- **`JwtTokenIssuer`** keeps generating the session id and `jti` itself. It is the implementation of the
  `TokenIssuer` port, so these ids already "come from a port" as Principle VIII requires.

**Rationale**: makes every rule and event deterministic in tests (SC-004) with the smallest new surface: one port with
one method.

**Alternatives considered**:
- *Let Hibernate keep generating the trip id and build the event after `save`*: rejected. The id would still come
  from infrastructure rather than a port, and unit tests would have to fake persistence just to get an id.
- *A `TimeProvider` port instead of `Clock`*: rejected. `Clock` is already injected in `AuthService` and
  `JwtTokenIssuer`, it is a JDK type, and `Clock.fixed(...)` is the standard test double.

## R6. Trip-creation rules move into the domain (G10, FR-006, FR-014, FR-015)

**Decision**:
- `CreateTripCommand` moves to `core/valueobject` (closing G2). It becomes
  `(long customerId, String serviceTypeName, Location pickup, Location destination, PaymentMethod paymentMethod)`.
  Its compact constructor rejects a non-positive `customerId`, a blank `serviceTypeName`, and null locations or
  payment method by throwing `InvalidTripException`. The `idempotencyKey` field is removed (see R8).
- A new static factory, `Trip.request(UUID id, long customerId, ServiceType serviceType, Location pickup,
  Location destination, FareCalculator fares, Instant now)`, does the following:
  - computes the distance;
  - throws `InvalidTripException("Pickup and destination must be different places")` when the distance is ≤ 0;
  - computes the fare;
  - sets status `REQUESTED` and `requestedAt = now`.

  The builder becomes private, so this factory is the only way to create a new trip. A package-private or test
  fixture builder can stay for tests and JPA.
- `FareCalculator` moves to `core/domainservice/FareCalculator` as a plain final class built from
  `(long perMeter, long minimum)`. It has no `@Component` and doesn't depend on `FareProperties`. `ApplicationConfig`
  creates the bean from `FareProperties`, which stays in `config`.
- `TripService.create` keeps today's order of checks, so the error a client sees for a request with several problems
  doesn't change:
  1. look up the service type → unknown: `InvalidTripException("Unknown service type, expected MyRide or MyCar")`
  2. reject an existing active trip → `ActiveTripExistsException`
  3. `Trip.request(...)` → distance rule
  4. `trips.add(trip)`
  5. `events.publish(Requested)`

**Rationale**: the trip owns its creation invariants (Principle VIII). The service is left to load, call, save and
publish. The package name `core.domainservice` avoids confusion with the top-level `service` package (application
services) and ArchUnit's `..service..` patterns.

**Alternatives considered**: *`core/service/FareCalculator`*: rejected because it's ambiguous next to the top-level
`service`. *Keep the distance check in the command*: rejected because the rule is about a trip, not about input
syntax.

## R7. Thin HTTP adapters (G11, FR-010)

**Decision**:
- A small `api/security/AuthenticatedCaller` helper with one method, `long userId(Jwt jwt)`, replaces the
  `jwt.getSubject() != null ? ... : 0` fallback and the `assert`. If `sub` is missing or not a number, it throws
  `core/exception/UnidentifiedCallerException`. This is a new domain exception for "the request can't be attributed
  to a user". It lives in `core/exception` because Principle I requires every failure to be a specific domain
  exception there. Parsing the JWT stays in `api`; only the failure type is domain-level.
- `GlobalExceptionHandler` maps that exception to `401` Problem Details with title `Unauthenticated` and a
  `WWW-Authenticate: Bearer` header, the same shape the authentication entry point already returns.
- `TripController` stops generating a UUID. `CreateTripRequest.serviceTypeId()` (the name-to-enum switch) is replaced
  by passing `serviceType` (the name) straight into the command.

**Rationale**: who the caller is and how to reject them is a transport concern. Which service types exist is a domain
concern (R9).

**Alternatives considered**: *A custom `HandlerMethodArgumentResolver` for `@CurrentUserId long`*: nicer at call
sites, but more machinery than two controllers need (Principle V). It's a reasonable step once more controllers
appear.

## R8. The trip request identifier (`idempotencyKey`)

**Decision**: remove the field from the command and stop generating it.

**Rationale**: the controller fills it with a fresh random UUID on every request, and nothing reads it. It isn't
stored, logged, or put in any event. So today it provides no deduplication, and removing it changes no behavior.
This satisfies the spec's edge case ("must not introduce or remove deduplication"). Removing the field instead of
moving it follows Principle I (no dead code) and Principle V (YAGNI). The spec's assumption that it "keeps its
current meaning" holds trivially, since it currently means nothing. Real idempotency (a client-supplied
`Idempotency-Key` header stored with the trip) would be a feature with its own spec.

**Alternatives considered**: *Generate it in `TripService` from `IdGenerator`*: rejected because it moves dead data
instead of removing it.

## R9. A single definition of "service type" (G12, FR-018)

**Decision**: the `core/entity/ServiceType` entity, which is backed by the `service_types` reference table, becomes
the one definition. The enum `core/valueobject/ServiceType` (`UNKNOWN`, `MY_RIDE`, `MY_CAR`) is deleted.

- The API passes the name it receives (`"MyRide"` or `"MyCar"`, case-sensitive) into the command.
- `TripService` resolves it with `ServiceTypeRepository.findActiveByName(name)`. An empty result produces the same
  `InvalidTripException` message as today, which the API returns as the same `400` (FR-019).
- `Trip.request(...)` takes the `ServiceType` and stores `serviceType.getId()`. With the seed data, `MyRide` has id 1
  and `MyCar` has id 2, the same numbers the old `ordinal()` produced. So stored rows and the `serviceTypeId` in
  `TripEvent.Requested` stay identical, and **no migration is needed**.

**Rationale**: the database already holds the authoritative list, with vehicle type, passenger limit and an active
flag. The enum duplicated it and depended on a coincidence of ordering. Looking up by name also lets a new service
type be added with a migration alone.

**Behavior note**: the lookup filters on `active = true`. Both seeded types are active, so nothing observable changes
today. A type deactivated in the future would be rejected as unknown, which is the evident intent of the `active`
column.

**Alternatives considered**: *Keep the enum as the single definition and drop the table*: rejected because it needs a
migration and the trips foreign key depends on the table. *Keep the enum and add a `code` mapping to the table*:
rejected because it still keeps two definitions in sync by hand.

## R10. Package moves and remaining placement fixes (G9, G13, FR-008, FR-011, FR-017)

**Decision**:

| Today | After |
|-------|-------|
| `common/FareCalculator` | `core/domainservice/FareCalculator` (R6) |
| `common/ProblemDetailWriter` | `api/handler/ProblemDetailWriter` |
| `adapter/JsonAuthenticationEntryPoint` | `api/handler/JsonAuthenticationEntryPoint`. It writes an HTTP response for an inbound request, so it belongs at the inbound edge. |
| `adapter/BcryptHasher` | `adapter/security/BcryptPasswordHasher` |
| `adapter/JwtTokenIssuer` | `adapter/security/JwtTokenIssuer` |
| `adapter/JwtProperties` | `adapter/security/JwtProperties`. Properties live with the adapter that reads them; `config` may depend on `adapter`, but not the other way round. |
| `adapter/KafkaPublisher` | `adapter/kafka/KafkaEventSender` + `AfterCommitEventPublisher` + `EventJsonMixin` + `TripEventsProperties` (R4) |
| `core/event/TripEventRelay` | removed; replaced by `AfterCommitEventPublisher` (R4) |
| `core/port/GeneralHasher` (static helper) | `core/port/TokenHasher` (`String hash(String token)`) + `adapter/security/Sha256TokenHasher` |
| `service/command/CreateTripCommand` | `core/valueobject/CreateTripCommand` (R6) |
| `core/valueobject/ServiceType` (enum) | removed (R9) |
| `@Transactional` from `jakarta.transaction` on `TripService` | Spring's `@Transactional`; `HelloService` gets `@Transactional(readOnly = true)` for consistency |
| `Clock` bean in `SecurityConfig` | `config/ApplicationConfig` (with `IdGenerator` and `FareCalculator` beans) |

Once these moves are done, `common` is empty and is deleted. The architecture rule for `Common` stays, so the package
can only come back holding technology-neutral helpers.

**On `@ConfigurationProperties` placement**: Principle IV's role table lists `@ConfigurationProperties` under
`config`. The dependency rule (`adapter` → `core` only) forbids an adapter from importing `config`. The dependency rule
is the MUST, so adapter-specific properties records live in their adapter package and are enabled from `config`.
Application-wide properties (`FareProperties`) stay in `config`. This is recorded in Complexity Tracking so a future
constitution amendment can make the table say so explicitly.

**Rationale**: a port with a static helper isn't a port (FR-008). Making token hashing a port lets the algorithm be
swapped or faked, and leaves `common` with nothing to hold.

## R11. Real-technology integration tests (FR-022, SC-008)

**Decision**: use **Testcontainers**, with the versions managed by Spring Boot 4.1's dependency BOM, together with
Spring Boot's `@ServiceConnection`. The test-scoped dependencies are:

- `org.springframework.boot:spring-boot-testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-postgresql`
- `org.testcontainers:testcontainers-kafka`

A shared `src/test/java/org/kore/myapp/TestcontainersConfiguration` (`@TestConfiguration(proxyBeanMethods = false)`)
declares two `@ServiceConnection` beans: `PostgreSQLContainer` with `postgres:16`, matching the README, and
`KafkaContainer` with `apache/kafka:3.9.0`. Spring's test-context cache shares one pair of containers across all
integration test classes.

| Test class | Slice | Proves |
|------------|-------|--------|
| `adapter/persistence/JpaTripRepositoryIT` | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import` of the adapter and Postgres container. Flyway runs the real migrations. | `add`/`update`/`findById`; `findActiveByCustomerId` finds each of the four active statuses and ignores `COMPLETED`/`CANCELLED`; **two concurrent `add`s for one customer** (two threads, a `CountDownLatch`, `TransactionTemplate`) → exactly one `ActiveTripExistsException`; a non-active-trip integrity violation (unknown service type id) is rethrown untranslated |
| `adapter/persistence/JpaTripOfferRepositoryIT`, `JpaDriverProfileRepositoryIT`, `JpaUserRepositoryIT`, `JpaSessionRepositoryIT`, `JpaServiceTypeRepositoryIT` | same slice | every port operation, including `findPending` ignoring non-pending offers and `findActiveByName` being case-sensitive |
| `adapter/kafka/AfterCommitEventPublisherIT` | `@SpringBootTest` with both containers, plus a plain `KafkaConsumer` from the transitive `kafka-clients` dependency | for each event type: topic `trip-events`, key = `tripId`, exact JSON payload; delivered only after commit; nothing delivered after a rollback (poll for 2 s); a send failure after commit is logged and not thrown |

`MyAppTests` (the context-load test) imports the same configuration, so it stops needing a hand-started local
Postgres.

When Docker isn't available, integration tests **fail rather than skip**: no `disabledWithoutDocker`. A skipped
adapter test would let the build pass without the evidence FR-022 requires. The container runtime is a documented
prerequisite (spec Assumptions). On this machine `docker` isn't currently on the `PATH`, so the developer needs
Docker Desktop, Colima or Podman before the integration tests can run.

Integration test classes end in `IT` but run in the normal Surefire `test` phase. Surefire's default includes are
widened to `**/*IT.java`, so `./mvnw test` stays the single gate that Principles II and III name, and no Failsafe
configuration is needed.

**Rationale**: Testcontainers with `@ServiceConnection` is the approach Spring Boot itself recommends, and it is the
approach the constitution names. Using the same images as the README keeps tests and local runs on the same versions.

**Alternatives considered**:
- *H2 or embedded Kafka*: rejected because Principle III forbids in-memory substitutes, and `uq_trips_customer_active`
  is a PostgreSQL partial unique index.
- *`spring-kafka-test`'s `EmbeddedKafka`*: rejected for the same reason.
- *Failsafe with a separate `verify` phase*: rejected because it creates a second gate that developers forget to run.

**Implementation notes (deviations found while building R11)**:
- The Kafka test image is `apache/kafka:3.9.1`, not the README's 3.9.0. Testcontainers 2 formats the broker's
  storage with an `advertised.listeners` value of `0.0.0.0`, which the 3.9.0 image rejects at startup; the 3.9.1
  patch release accepts it. Local runs from the README still use 3.9.0.
- `src/test/resources/config/application.properties` supplies `DB_USERNAME`, `DB_PASSWORD` and a fixed, clearly fake
  `APP_JWT_SECRET`, so the suite never depends on a developer's git-ignored `.env` and never reads a real secret.
  `classpath:/config/application.properties` takes precedence over the main `application.properties`.
- Every persistence integration test uses one meta-annotation (`@PersistenceAdapterTest`), so they share a single
  cached Spring context and one pair of containers.
- The architecture-rule fixtures for AR-02 live in `adapter/fixtureone` and `adapter/fixturetwo`. In
  `adapter/archfixture/one|two` (as T010 first said) both classes fall into the same `adapter.(*)` slice, so the
  rule could never fire on them.

## R12. Migrating the existing unit tests (FR-020)

**Decision**:
- `TripServiceTest` is rewritten against the new ports with Mockito, a `Clock.fixed(...)`, a sequential `IdGenerator`
  lambda, and an in-memory `RecordingEventPublisher` that keeps a list of published events.
- Its two exception-translation tests (`createReportsActiveTripWhenConcurrentInsertHitsUniqueIndex`,
  `createRethrowsOtherIntegrityViolations`) and `activeTripLookupCoversEveryUnfinishedStatus` move to
  `JpaTripRepositoryIT` with the same assertions, now against the real index.
- A new service-level test asserts that an `ActiveTripExistsException` thrown by `TripRepository.add` reaches the
  caller.
- `TripTest` and `CoordinateTest` keep their assertions. `TripTest` gains tests for `Trip.request` (status, fare,
  distance rule, `requestedAt`, id from the generator).
- New tests are added for `OfferServiceTest`, `AuthServiceTest`, `CreateTripCommandTest`, `FareCalculatorTest`,
  `AuthenticatedCallerTest`, and the `@WebMvcTest` cases for the 401 on a missing or invalid subject.

**Rationale**: an assertion is moved, never dropped. It goes to the layer that now owns the behavior, so "no weakened
assertions" (FR-020) holds.

## R13. Delivery order

**Decision**: one pull request per user story, in spec priority order. Within a story, follow the constitution's
inside-out order: domain → ports → services → outbound adapters with integration tests → inbound adapters → docs.

1. **US1** adds `ArchitectureTest` with every rule frozen. The store records today's violations (expected: about 40,
   across G2–G13), and the suite goes green.
2. **US2** (ports, command, clock and ids, domain rules, service type) adds the test infrastructure and persistence
   adapter tests, since US2 creates the persistence adapters.
3. **US3** (exception translation, events after commit, thin controllers, adapter grouping) adds the Kafka integration
   test.
4. **US4** (layout, README, CHANGELOG, constitution TODOs) removes `freeze(...)` and deletes the store.

Each PR leaves `./mvnw test` green and the frozen store smaller.

**Rationale**: matches the spec's assumption of incremental delivery and keeps every PR reviewable on its own.

## R14. Flyway auto-configuration (found during implementation)

**Decision**: replace `org.flywaydb:flyway-core` with `org.springframework.boot:spring-boot-starter-flyway`, which
brings `flyway-core` and Spring Boot's `spring-boot-flyway` auto-configuration module. Keep
`flyway-database-postgresql`.

**Rationale**: Spring Boot 4 split auto-configuration into per-technology modules. With only `flyway-core` on the
classpath, no Flyway bean is created, so `src/main/resources/db/migration` never ran. A fresh database then failed
Hibernate's `ddl-auto=validate` (`missing table [driver_profiles]`). The README and Principle V already treat
Flyway-on-startup as the way the schema is managed. Without this fix, `MyAppTests` and every persistence integration
test (FR-022) can't run against a real, empty PostgreSQL. `MyAppTests` was the failing regression test for it
(Principle II).

**Consequence**: the application now migrates its database on startup. A developer database whose tables were created
without Flyway's history table makes startup fail with a "non-empty schema" error. Either baseline it once
(`spring.flyway.baseline-on-migrate=true` for one run) or recreate it.

**Alternatives considered**: *Run Flyway only from tests (`@FlywayTest`/manual `Flyway.migrate()`)*: rejected because
the application would keep starting against an unmigrated database, contradicting the README.
