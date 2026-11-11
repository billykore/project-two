# Implementation Plan: Hexagonal Architecture Compliance

**Branch**: `001-hexagonal-architecture-compliance` | **Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-hexagonal-architecture-compliance/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

The project already uses ports-and-adapters vocabulary, but it doesn't yet follow the rules. The survey confirmed all
13 gaps in the spec and found three more (research, "Additional findings"):
- the storage ports are Spring Data interfaces;
- a command lives in `service` but is used by `core`;
- the domain hosts a Spring event relay and a Jackson annotation;
- the services read the wall clock and catch Hibernate exceptions;
- offer events are sent before commit;
- "service type" exists twice and is linked through `enum.ordinal()`;
- `common` holds a domain service and an HTTP helper.

The approach works from the outside in for safety, then inside-out for each change:

1. **Lock the rule first.** ArchUnit encodes Principles IV, VI, VII and VIII as nine rules (AR-01 to AR-09). Today's
   violations are frozen in a store that can only shrink (research R1).
2. **Make the ports the application's own.** Six storage ports become plain interfaces implemented by
   `adapter/persistence`, with Spring Data hidden behind them. New `IdGenerator` and `TokenHasher` ports join an
   injected `Clock`. `CreateTripCommand` moves into `core`. Trip-creation rules move into `Trip.request(...)`. The
   service-type enum is removed in favor of the `service_types` reference entity (R2, R5, R6, R9).
3. **Push infrastructure to the edge.** Constraint-violation translation moves into `JpaTripRepository`. A single
   `EventPublisher` port is implemented by an after-commit Kafka adapter, which also fixes offer events. Controllers
   stop inventing ids and defaulting identities. Adapters are grouped into `persistence`, `kafka` and `security`
   (R3, R4, R7, R10).
4. **Prove it against the real thing.** Testcontainers (PostgreSQL 16 and Kafka 3.9 via `@ServiceConnection`) backs
   integration tests for every adapter this feature creates or changes, including the concurrent duplicate-trip and
   rollback cases (R11).
5. **Finish.** Unfreeze the rules, delete the store, and update the README, CHANGELOG and the constitution's TODOs.

HTTP and event payloads stay identical. Two documented behavior changes remain: offer events are delivered after
commit, and an unusable JWT subject gives 401.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**:
- already in use: Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server, Kafka), Lombok,
  Flyway;
- **added for tests only**:
  - `com.tngtech.archunit:archunit-junit5` (1.4.x, pinned);
  - `org.springframework.boot:spring-boot-testcontainers`;
  - `org.testcontainers:testcontainers-junit-jupiter`, `testcontainers-postgresql` and `testcontainers-kafka`
    (versions managed by the Boot BOM).

**Runtime dependency change**: `org.flywaydb:flyway-core` is replaced by
`org.springframework.boot:spring-boot-starter-flyway` (research R14). Spring Boot 4 moved Flyway auto-configuration
into its own module, so with `flyway-core` alone migrations never ran. The README already claims they run on startup,
and the integration tests depend on it. No other runtime dependency is added.

**Storage**: PostgreSQL (16 in tests, 13+ supported) with Flyway. No schema change and no new migration.

**Testing**:
- JUnit 5, AssertJ, Mockito for unit tests;
- `@WebMvcTest` for the controllers that change;
- `@DataJpaTest` and `@SpringBootTest` with Testcontainers for the adapters;
- ArchUnit for the architecture rules.

Everything runs in `./mvnw test` (Surefire includes `**/*IT.java`).

**Target Platform**: Linux/macOS JVM service. Tests need a Docker-compatible container runtime.

**Project Type**: web service (single Spring Boot application, hexagonal layout)

**Performance Goals**: no change. The one new database round-trip is the `service_types` lookup by name on
`POST /trips`. It is a primary-key-sized table, so the cost is negligible.

**Constraints**:
- HTTP and event contracts stay identical (FR-019), with two exceptions: offer events after commit, and 401 for an
  unusable subject;
- the existing suite stays green after every PR;
- the frozen violation store only shrinks;
- no merged migration is edited.

**Scale/Scope**:
- about 70 production classes;
- roughly 30 touched, about 15 added (adapters, ports, tests support) and about 6 removed (`common/*`, `core/event`,
  `service/command`, the service-type enum, `GeneralHasher`, `KafkaPublisher`);
- 4 user stories delivered as 4 PRs.

All Technical Context items are resolved. No NEEDS CLARIFICATION remains (see [research.md](./research.md)).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Gates from `.specify/memory/constitution.md` (v2.0.0). Mark each ✅ pass, or ❌ with a row in Complexity Tracking.

### Pre-research gate

This feature exists to bring the code into compliance. The gate therefore asks whether the **plan** complies and
moves the code toward compliance without making anything worse (Quality Gates §5). It doesn't ask whether the code
complies today.

- [x] ✅ **I. Clean, Readable Code**:
  - new names follow the domain (`TripRepository`, `IdGenerator`, `TokenHasher`, `AfterCommitEventPublisher`);
  - constructor injection throughout;
  - `CreateTripCommand` stays an immutable record and gains self-validation;
  - dead `idempotencyKey` removed (R8);
  - failures stay specific domain exceptions mapped to Problem Details. The new "caller can't be identified"
    failure is `core/exception/UnidentifiedCallerException` (R7).
- [x] ✅ **II. Test Discipline**:
  - every moved rule keeps its assertions at its new home (R12);
  - the two behavior changes are bug fixes and start with failing regression tests:
    - the rollback case for `DriverAssigned`/`Rejected` in `AfterCommitEventPublisherIT`;
    - the missing or non-numeric `sub` cases in the controller tests;
  - domain and service tests run without a Spring context, using a fixed `Clock` and `IdGenerator`.
- [x] ✅ **III. Integration & Contract Verification**:
  - every adapter this feature creates or changes has a Testcontainers test (R11);
  - the concurrency rule `uq_trips_customer_active` has a real conflicting-insert test;
  - every event type has a topic, key, payload and after-commit test;
  - the changed controllers get `@WebMvcTest`s.
  The untouched `AuthController` and `HelloController` aren't brought up to Principle III here. That gap already
  exists and isn't made worse (see Complexity Tracking).
- [x] ✅ **IV. Hexagonal Boundaries**: every class gets its role package (data-model, "Package roles"), and the
  ArchUnit test is added. Two temporary or interpretive deviations are listed in Complexity Tracking: the frozen
  store during US1–US3, and properties records living in adapters.
- [x] ✅ **V. Maintainability & Simplicity**:
  - the test dependencies are justified (R1, R11), and the one runtime dependency change, the Flyway starter, is
    justified in R14;
  - no migration edited or added;
  - `@Value` replaced by typed `TripEventsProperties`;
  - transactional outbox, separate persistence model, Maven modules and an argument resolver were considered and
    rejected as speculative;
  - each PR updates the CHANGELOG and the README sections it affects in the same change (see Delivery slices). US4
    only rewrites the Architecture tree.
- [x] ✅ **VI. Ports Are Owned by the Application**: all ports become plain, framework-free and declaration-only
  (contracts/ports.md); the command moves to `core`; `@throws` is documented. There is one narrow deviation: unexpected
  infrastructure failures, including commit-time ones, propagate untranslated (R3, Complexity Tracking).
- [x] ✅ **VII. Adapters Translate, Never Decide**:
  - controllers only extract, map and call one use case;
  - outbound adapters are grouped by technology;
  - constraint translation happens in the persistence adapter;
  - the JSON shape is decided in the Kafka adapter (mix-in);
  - no adapter-to-adapter dependency, enforced by AR-02.
- [x] ✅ **VIII. Domain Model at the Center**:
  - `Trip.request` owns the creation rules;
  - `FareCalculator` is a plain domain service;
  - `core` has no Spring or Jackson types;
  - time and ids are injected;
  - `@Transactional` (Spring) is only in `service`;
  - events go to the `EventPublisher` port, and the adapter decides when they are delivered;
  - the Hibernate-specific mapping annotations kept on entities are a recorded deviation (Complexity Tracking).

**Result: PASS.** Proceed to Phase 0.

### Post-design re-check (after Phase 1)

I re-evaluated the design against [data-model.md](./data-model.md) and [contracts/](./contracts/):

- **IV**: the package map in data-model.md fits the Principle IV table. AR-01 to AR-09 cover every MUST in IV, VI, VII
  and VIII that can be checked statically. `JsonAuthenticationEntryPoint` moves to `api.handler` because it is an
  inbound HTTP concern. `config` → `api` is allowed.
- **VI**: contracts/ports.md has no framework types, no static or default methods, and declares only the operations
  the services call (checked against the flows in data-model.md).
- **VII**: `AfterCommitEventPublisher` and `KafkaEventSender` sit in the same `adapter.kafka` slice, so this isn't
  adapter-to-adapter coupling. The api layer depends only on use-case ports (AR-09).
- **VIII**:
  - `Trip.version` becomes `Long`, which is a mapping concern that stays in the entity;
  - `@CreationTimestamp`, `@UpdateTimestamp` and `@JdbcTypeCode` are Hibernate-specific, not JPA, so they fall
    outside Principle VIII's mapping exception. They are kept as a recorded deviation (Complexity Tracking, R5) and
    resolved by the T075 constitution PATCH;
  - `JwtTokenIssuer` generates session and token ids, which is acceptable because it is the port implementation.
- **III**: the golden JSON fixtures are captured *before* the refactor (contracts/events.md), so payload equality is
  proven, not assumed.
- **II**: R12 maps every existing assertion to its new home. None is dropped.

**Result: PASS.** No unjustified violations. `/speckit-analyze` found that the Hibernate mapping annotations were
not recorded, and they have now been added to Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-hexagonal-architecture-compliance/
├── spec.md                  # Feature specification (/speckit-specify)
├── plan.md                  # This file (/speckit-plan)
├── research.md              # Phase 0: decisions R1–R13
├── data-model.md            # Phase 1: domain model, package map, service flows
├── quickstart.md            # Phase 1: validation guide
├── contracts/
│   ├── ports.md             # Inbound/outbound port signatures
│   ├── events.md            # trip-events topic contract + delivery guarantee
│   ├── http-api.md          # Unchanged HTTP contract + the 401 change
│   └── architecture-rules.md# ArchUnit rules AR-01..AR-09 + freeze store policy
├── checklists/
│   └── requirements.md      # Spec quality checklist
└── tasks.md                 # Phase 2 output (/speckit-tasks; not created by /speckit-plan)
```

### Source Code (repository root)

The target layout is shown below. Markers: `+` new, `~` moved or changed, `−` removed. Unmarked files are unchanged.

```text
src/main/java/org/kore/myapp/
├── MyApp.java
├── core/
│   ├── entity/              ~ Trip (factory, assigned id, Long version, no setters), ~ User (createdAt mapping),
│   │                          ServiceType (now the single definition), TripOffer, DriverProfile, Session
│   ├── valueobject/         + CreateTripCommand (from service/command), ~ TripEvent (no @JsonProperty),
│   │                          − ServiceType enum; Coordinate, Location, Money, Token, Event, enums
│   ├── domainservice/       + FareCalculator (from common, plain class)
│   ├── exception/           + UnidentifiedCallerException; existing exceptions unchanged
│   ├── port/                ~ 6 repositories (plain interfaces), + IdGenerator, + TokenHasher, − GeneralHasher,
│   │                          ~ TripUseCase (new command package); EventPublisher, TokenIssuer, PasswordHasher, *UseCase
│   └── event/               − TripEventRelay (replaced by adapter/kafka)
├── service/                 ~ TripService, ~ OfferService, ~ AuthService, ~ HelloService (Spring @Transactional,
│                              Clock, IdGenerator); − command/
├── api/
│   ├── controller/          ~ TripController, ~ DriverOfferController (no id generation, no identity fallback)
│   ├── request/             ~ CreateTripRequest (passes the service-type name through)
│   ├── response/
│   ├── handler/             ~ GlobalExceptionHandler (+401 mapping), + ProblemDetailWriter, + JsonAuthenticationEntryPoint
│   └── security/            + AuthenticatedCaller
├── adapter/
│   ├── persistence/         + Jpa{Trip,TripOffer,DriverProfile,User,Session,ServiceType}Repository (port impls)
│   │                        + SpringData*Repository (package-private Spring Data interfaces)
│   ├── kafka/               + AfterCommitEventPublisher, + KafkaEventSender, + EventJsonMixin, + TripEventsProperties
│   │                        − (KafkaPublisher)
│   └── security/            ~ JwtTokenIssuer, ~ JwtProperties, ~ BcryptPasswordHasher (was BcryptHasher), + Sha256TokenHasher
├── config/                  ~ SecurityConfig (enables adapter properties; Clock bean moved out), + ApplicationConfig,
│                              FareProperties
└── common/                  − (FareCalculator → core.domainservice, ProblemDetailWriter → api.handler)

src/test/java/org/kore/myapp/
├── ArchitectureTest.java            + AR-01..AR-09
├── TestcontainersConfiguration.java + PostgreSQL 16 + Kafka 3.9 @ServiceConnection beans
├── MyAppTests.java                  ~ imports TestcontainersConfiguration
├── core/
│   ├── entity/TripTest.java         ~ + Trip.request cases
│   ├── valueobject/                 CoordinateTest, + CreateTripCommandTest
│   └── domainservice/               + FareCalculatorTest
├── service/                         ~ TripServiceTest, + OfferServiceTest, + AuthServiceTest,
│   └── support/                     + RecordingEventPublisher
├── api/                             + TripControllerTest, + DriverOfferControllerTest, + AuthenticatedCallerTest
└── adapter/
    ├── persistence/                 + Jpa*RepositoryIT (6)
    └── kafka/                       + AfterCommitEventPublisherIT

src/test/resources/
├── archunit.properties              + (removed again at the end of US4)
├── archunit_store/                  + frozen violations (shrinks; removed at the end of US4)
└── events/                          + golden JSON fixtures per event type
```

**Structure Decision**: keep the single-module layout of constitution Principle IV. Add the technology subpackages
`adapter/persistence`, `adapter/kafka` and `adapter/security`, as the constitution prescribes, and a
`core/domainservice` subpackage for rules that span aggregates or need configuration. Remove `common`, `core/event`
and `service/command`. No new top-level package is introduced.

### Delivery slices (detailed in `tasks.md` by `/speckit-tasks`)

| PR | Story | Contents | Store after merge |
|----|-------|----------|-------------------|
| 1 | US1 (P1) | ArchUnit dependency, `ArchitectureTest` with AR-01 to AR-09 frozen, `archunit.properties`, initial store | all current violations recorded |
| 2 | US2 (P2) | Testcontainers setup; capture event JSON fixtures; plain ports + `adapter/persistence` + `Jpa*IT`; `IdGenerator`, `Clock` in services; `CreateTripCommand` to core; `Trip.request`; `FareCalculator` to `core.domainservice`; service-type consolidation; `TokenHasher`; unit tests rewritten | G2, G3, G4, G8, G10, G12 entries gone |
| 3 | US3 (P3) | `adapter/kafka` after-commit publisher + `AfterCommitEventPublisherIT` (rollback regression test first); remove `TripEventRelay` and the Spring event bus; constraint translation in the adapter; thin controllers + 401 + `@WebMvcTest`s; adapter grouping; Spring `@Transactional` everywhere; **README Booking flow and API section + CHANGELOG for the after-commit offer events and the 401** | G5, G6, G7, G9, G11, G13 entries gone; store empty |
| 4 | US4 (P4) | Remove `freeze(...)`, delete the store and properties; README Architecture tree; CHANGELOG entry for the restructuring; constitution Sync Impact Report TODOs marked resolved, plus the 2.0.1 PATCH | — |

**Docs ship with the change (Principles III and V).** Each PR updates `CHANGELOG.md` (Unreleased) and the README
sections it affects, in that same PR:
- the PR that makes tests need a container runtime documents that prerequisite (README "Running the tests" +
  CHANGELOG);
- PR 3 documents the two contract changes;
- no PR defers a user-visible change's docs to a later PR.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **IV**: known violations tolerated by the frozen ArchUnit store during PRs 1–3 | Lets the guard land first (US1) and catch *new* violations while the existing ones are paid down in small, reviewable PRs | Fixing everything before adding the guard means one very large PR with no protection meanwhile. Adding the guard last leaves PRs 2–3 unchecked. The store is deleted in PR 4. |
| **IV** (role table): adapter-specific `@ConfigurationProperties` records (`JwtProperties`, `TripEventsProperties`) live in their adapter package, not in `config` | The dependency rule forbids `adapter` → `config`, and these records are read by adapters. `config` still enables them. | Putting them in `config` breaks the MUST-level dependency rule. Passing raw values into adapters through `config` `@Bean` methods would spread adapter wiring into `config` for no benefit. The T075 constitution PATCH states this explicitly. |
| **VI**: unexpected infrastructure failures (connection loss, foreign-key violations, commit-time optimistic-lock conflicts) propagate untranslated and give 500 | Commit-time exceptions come from the transaction proxy after the adapter has returned, so no adapter can translate them. Translating the rest would add a wrapper to every method with no visible change. | A generic `DataStoreException` wrapper in every adapter method, or an AOP aspect (new dependency), would still miss commit-time failures. Flushing on every write changes transaction behavior and costs round-trips. Expected business conflicts (active trip) **are** translated. |
| **VIII**: Hibernate-specific mapping annotations stay on domain entities: `@CreationTimestamp`/`@UpdateTimestamp` on `Trip.createdAt`/`updatedAt` (the first reads the system clock) and `@JdbcTypeCode(SqlTypes.CHAR)` on `Session.tokenHash`. Principle VIII permits only *JPA* mapping annotations. | They are pure persistence mapping. The audit timestamps are read by no rule, response or event, and `@JdbcTypeCode` lets `ddl-auto=validate` accept the `CHAR(64)` column. AR-04 confines them to `core.entity`. T075 resolves this with a constitution PATCH that names provider-specific mapping annotations as part of the mapping exception, as long as no rule or event depends on them. | Database-side timestamps need a new migration with an `updated_at` trigger for columns nothing reads. Setting them from the injected `Clock` in every service puts persistence bookkeeping into application code. A JPA-only `columnDefinition` for `token_hash` isn't guaranteed to satisfy schema validation. |
| **III** (pre-existing, not worsened): `AuthController` and `HelloController` still lack `@WebMvcTest`s | The spec (FR-022, option A) scopes web-layer and real-technology tests to what this feature creates or changes. The `adapter/security` classes talk to no external system (in-process JWT signing and hashing), so plain unit tests cover them and they need no container. | Covering every endpoint is the rest of `TODO(INTEGRATION_TEST_INFRA)`. It is tracked as follow-up and made much cheaper by the Testcontainers setup this feature adds. |
