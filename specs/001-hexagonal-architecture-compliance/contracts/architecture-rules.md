# Contract: Architecture rules (`src/test/java/org/kore/myapp/ArchitectureTest.java`)

These rules are executable. `./mvnw test` fails when any of them is broken, and the failure message names the
violating class and the dependency it uses (SC-002). Every rule comes from the constitution, as cited.

## Layers (Principle IV)

| Layer | Packages | May be accessed by |
|-------|----------|--------------------|
| Core | `org.kore.myapp.core..` | Service, Api, Adapter, Config |
| Service | `org.kore.myapp.service..` | Config |
| Api | `org.kore.myapp.api..` | Config |
| Adapter | `org.kore.myapp.adapter..` | Config |
| Config | `org.kore.myapp.config..` | — (composition root) |
| Common (optional) | `org.kore.myapp.common..` | any layer; may itself access no layer |

`MyApp` (the `@SpringBootApplication` class in the root package) is outside every layer.

## Rules

| ID | Rule | Constitution |
|----|------|--------------|
| AR-01 | The layer access matrix above holds. | IV |
| AR-02 | Slices `org.kore.myapp.adapter.(*)..` don't depend on each other. | VII |
| AR-03 | No class in `core..` depends on `org.springframework..`, `org.apache.kafka..`, `jakarta.servlet..`, `tools.jackson..` or `com.fasterxml.jackson..`. | VIII |
| AR-04 | No class in `core.port..`, `core.valueobject..`, `core.exception..` or `core.domainservice..` depends on `jakarta.persistence..` or `org.hibernate..`. Mapping is allowed only in `core.entity..`. | VI, VIII |
| AR-05 | Interfaces in `core.port..` declare no static methods and no default methods, and extend no type outside `org.kore.myapp..` and `java..`. | VI |
| AR-06 | Classes annotated or meta-annotated with `@Transactional` (Spring) reside in `service..`. Nothing uses `jakarta.transaction.Transactional`. | VIII |
| AR-07 | No class in `core..` or `service..` calls `Instant.now()`, `LocalDateTime.now()`, `LocalDate.now()`, `Clock.systemUTC()`, `Clock.systemDefaultZone()` or `UUID.randomUUID()`. | VIII |
| AR-08 | No class in `core..` is annotated with a Spring stereotype (`@Component`, `@Service`, `@Repository`, `@Configuration`) or with `@EventListener` / `@TransactionalEventListener`. | VIII (redundant with AR-03, kept for a clearer message) |
| AR-09 | Classes in `api..` don't depend on `core.port` outbound ports (`*Repository`, `EventPublisher`, `TokenIssuer`, `PasswordHasher`, `TokenHasher`, `IdGenerator`). Controllers call use cases only. | VII |

## Known-exception store

- **During US1–US3**, each rule is wrapped in `FreezingArchRule.freeze(...)`, and the store lives in
  `src/test/resources/archunit_store/`. `archunit.properties` sets `freeze.store.default.allowStoreCreation=false`
  and `freeze.refreeze=false`:
  - a violation that isn't in the store fails the build;
  - a violation that has been fixed is removed from the store on the next run, and that change is committed;
  - nobody may add lines to the store by hand. Reviewers reject any diff that adds them.
- **At the end of US4**, `freeze(...)` is removed, the store and properties are deleted, and every rule runs
  unfrozen (SC-001).
