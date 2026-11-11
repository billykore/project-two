package org.kore.myapp;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kore.myapp.adapter.fixtureone.AdapterOne;
import org.kore.myapp.adapter.fixturetwo.AdapterTwo;
import org.kore.myapp.api.archfixture.ApiTransactional;
import org.kore.myapp.api.archfixture.ApiUsesRepository;
import org.kore.myapp.core.archfixture.CoreCallsClock;
import org.kore.myapp.core.archfixture.CoreDependsOnService;
import org.kore.myapp.core.archfixture.CoreUsesSpring;
import org.kore.myapp.core.port.TripRepository;
import org.kore.myapp.core.port.archfixture.PortWithDefaultMethod;
import org.kore.myapp.core.port.archfixture.PortWithJpa;
import org.kore.myapp.service.HelloService;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that every architecture rule fires on a deliberately violating fixture and that its report names both ends of
 * the forbidden dependency (SC-002). The fixtures live in test sources, which {@link ArchitectureTest} doesn't import.
 */
class ArchitectureRulesSelfTest {

    private static JavaClasses fixtures;

    @BeforeAll
    static void importFixtures() {
        fixtures = new ClassFileImporter().importClasses(
                CoreDependsOnService.class, HelloService.class,
                CoreUsesSpring.class, CoreCallsClock.class,
                PortWithDefaultMethod.class, PortWithJpa.class,
                AdapterOne.class, AdapterTwo.class,
                ApiUsesRepository.class, TripRepository.class,
                ApiTransactional.class);
    }

    static Stream<Arguments> rules() {
        return Stream.of(
                Arguments.of("AR-01", ArchitectureRules.LAYERS,
                        CoreDependsOnService.class.getName(), HelloService.class.getName()),
                Arguments.of("AR-02", ArchitectureRules.ADAPTERS_INDEPENDENT,
                        AdapterOne.class.getName(), AdapterTwo.class.getName()),
                Arguments.of("AR-03", ArchitectureRules.CORE_FRAMEWORK_FREE,
                        CoreUsesSpring.class.getName(), "org.springframework.stereotype.Component"),
                Arguments.of("AR-04", ArchitectureRules.MAPPING_ONLY_IN_ENTITIES,
                        PortWithJpa.class.getName(), "jakarta.persistence.EntityManager"),
                Arguments.of("AR-05 (static/default methods)", ArchitectureRules.PORTS_ARE_PLAIN,
                        PortWithDefaultMethod.class.getName() + ".helper()",
                        PortWithDefaultMethod.class.getName() + ".withLogic()"),
                Arguments.of("AR-05 (framework supertype)", ArchitectureRules.PORTS_ARE_PLAIN,
                        PortWithJpa.class.getName(), "org.springframework.data.repository.Repository"),
                Arguments.of("AR-06 (annotation placement)", ArchitectureRules.TRANSACTIONS_IN_SERVICES,
                        ApiTransactional.class.getName(), "is annotated with @Transactional"),
                Arguments.of("AR-06 (jakarta annotation)", ArchitectureRules.TRANSACTIONS_IN_SERVICES,
                        ApiTransactional.class.getName(), "jakarta.transaction.Transactional"),
                Arguments.of("AR-07 (clock)", ArchitectureRules.NO_AMBIENT_TIME_OR_IDS,
                        CoreCallsClock.class.getName(), "java.time.Instant.now()"),
                Arguments.of("AR-07 (ids)", ArchitectureRules.NO_AMBIENT_TIME_OR_IDS,
                        CoreCallsClock.class.getName(), "java.util.UUID.randomUUID()"),
                Arguments.of("AR-08", ArchitectureRules.NO_SPRING_COMPONENTS_IN_CORE,
                        CoreUsesSpring.class.getName(), "is meta-annotated with @Component"),
                Arguments.of("AR-09", ArchitectureRules.API_USES_ONLY_USE_CASES,
                        ApiUsesRepository.class.getName(), TripRepository.class.getName())
        );
    }

    @ParameterizedTest(name = "{0} fires and names both ends")
    @MethodSource("rules")
    void ruleReportsTheViolation(String id, ArchRule rule, String violator, String target) {
        EvaluationResult result = rule.evaluate(fixtures);

        assertThat(result.hasViolation()).as("%s has a violation", id).isTrue();
        assertThat(result.getFailureReport().toString()).contains(violator, target);
    }
}
