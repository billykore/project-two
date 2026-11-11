package org.kore.myapp;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The hexagonal architecture rules AR-01..AR-09 of the constitution (Principles IV, VI, VII, VIII), as executable
 * ArchUnit rules. See {@code specs/001-hexagonal-architecture-compliance/contracts/architecture-rules.md}.
 * <p>
 * {@link ArchitectureTest} applies them to the production code; {@link ArchitectureRulesSelfTest} proves each one
 * actually fires on a deliberately violating fixture.
 */
final class ArchitectureRules {

    private static final String ROOT = "org.kore.myapp";
    private static final String CORE = ROOT + ".core..";
    private static final String SERVICE = ROOT + ".service..";
    private static final String API = ROOT + ".api..";
    private static final String ADAPTER = ROOT + ".adapter..";
    private static final String CONFIG = ROOT + ".config..";
    private static final String COMMON = ROOT + ".common..";

    /** AR-01: dependencies point inward; only the composition root may see services and adapters. */
    static final ArchRule LAYERS = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Core").definedBy(CORE)
            .layer("Service").definedBy(SERVICE)
            .layer("Api").definedBy(API)
            .layer("Adapter").definedBy(ADAPTER)
            .layer("Config").definedBy(CONFIG)
            .layer("Common").definedBy(COMMON)
            .whereLayer("Core").mayOnlyBeAccessedByLayers("Service", "Api", "Adapter", "Config")
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Api").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Config").mayNotBeAccessedByAnyLayer()
            .whereLayer("Common").mayNotAccessAnyLayer()
            .because("of the dependency rule (constitution Principle IV)");

    /** AR-02: each adapter technology stands alone; flows go through ports. */
    static final ArchRule ADAPTERS_INDEPENDENT = slices().matching(ROOT + ".adapter.(*)..")
            .should().notDependOnEachOther()
            .because("adapters must not call other adapters (constitution Principle VII)");

    /** AR-03: the domain and its ports are free of Spring, Kafka, servlet and JSON types. */
    static final ArchRule CORE_FRAMEWORK_FREE = noClasses().that().resideInAPackage(CORE)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.apache.kafka..", "jakarta.servlet..",
                    "tools.jackson..", "com.fasterxml.jackson..")
            .because("the domain stays free of infrastructure (constitution Principle VIII)");

    /** AR-04: persistence mapping is allowed on entities only. */
    static final ArchRule MAPPING_ONLY_IN_ENTITIES = noClasses().that().resideInAnyPackage(
                    ROOT + ".core.port..", ROOT + ".core.valueobject..",
                    ROOT + ".core.exception..", ROOT + ".core.domainservice..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
            .because("only entities may carry persistence mapping (constitution Principles VI and VIII)");

    /** AR-05: ports are plain interfaces with declarations only. */
    static final ArchRule PORTS_ARE_PLAIN = classes().that().resideInAPackage(ROOT + ".core.port..")
            .and().areInterfaces()
            .should(declareOnlyAbstractMethodsAndExtendOnlyOwnTypes())
            .because("ports are owned by the application (constitution Principle VI)");

    /** AR-06: transactions are demarcated on application services only, with Spring's annotation. */
    static final ArchRule TRANSACTIONS_IN_SERVICES = CompositeArchRule
            .of(noClasses().that().resideOutsideOfPackage(SERVICE)
                    .should().beAnnotatedWith(Transactional.class))
            .and(noMethods().that().areDeclaredInClassesThat().resideOutsideOfPackage(SERVICE)
                    .should().beAnnotatedWith(Transactional.class))
            .and(noClasses().should().dependOnClassesThat()
                    .haveFullyQualifiedName("jakarta.transaction.Transactional"))
            .because("transactions are demarcated in application services (constitution Principle VIII)");

    /** AR-07: time and identifiers come from an injected Clock or IdGenerator. */
    static final ArchRule NO_AMBIENT_TIME_OR_IDS = noClasses().that().resideInAnyPackage(CORE, SERVICE)
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(Clock.class, "systemUTC")
            .orShould().callMethod(Clock.class, "systemDefaultZone")
            .orShould().callMethod(UUID.class, "randomUUID")
            .because("time and ids must be injected so rules are deterministic (constitution Principle VIII)");

    /** AR-08: no Spring components or event listeners in the domain. */
    static final ArchRule NO_SPRING_COMPONENTS_IN_CORE = CompositeArchRule
            .of(noClasses().that().resideInAPackage(CORE).should().beMetaAnnotatedWith(Component.class))
            .and(noMethods().that().areDeclaredInClassesThat().resideInAPackage(CORE)
                    .should().beMetaAnnotatedWith(EventListener.class))
            .because("the domain must not depend on Spring beans (constitution Principle VIII)");

    /** AR-09: inbound adapters call use cases, never outbound ports. */
    static final ArchRule API_USES_ONLY_USE_CASES = noClasses().that().resideInAPackage(API)
            .should().dependOnClassesThat().haveNameMatching(
                    "org\\.kore\\.myapp\\.core\\.port\\.(TripRepository|TripOfferRepository|DriverProfileRepository"
                            + "|UserRepository|SessionRepository|ServiceTypeRepository|EventPublisher|TokenIssuer"
                            + "|PasswordHasher|TokenHasher|IdGenerator)")
            .because("controllers call one use case and nothing else (constitution Principle VII)");

    private ArchitectureRules() {
    }

    private static ArchCondition<JavaClass> declareOnlyAbstractMethodsAndExtendOnlyOwnTypes() {
        return new ArchCondition<>("declare only abstract methods and extend only application or JDK types") {
            @Override
            public void check(JavaClass port, ConditionEvents events) {
                for (JavaMethod method : port.getMethods()) {
                    if (method.getModifiers().contains(JavaModifier.SYNTHETIC)) {
                        continue;
                    }
                    if (method.getModifiers().contains(JavaModifier.STATIC)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " is a static method in a port"));
                    } else if (!method.getModifiers().contains(JavaModifier.ABSTRACT)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " is a default method with logic in a port"));
                    }
                }
                port.getRawInterfaces().stream()
                        .filter(type -> !type.getName().startsWith(ROOT + ".") && !type.getName().startsWith("java."))
                        .forEach(type -> events.add(SimpleConditionEvent.violated(port,
                                port.getName() + " extends framework type " + type.getName())));
            }
        };
    }
}
