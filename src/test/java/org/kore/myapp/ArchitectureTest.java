package org.kore.myapp;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the hexagonal architecture rules ({@link ArchitectureRules}) on the production code. Any violation fails
 * {@code ./mvnw test} and names both ends of the forbidden dependency.
 */
@AnalyzeClasses(packages = "org.kore.myapp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layers = ArchitectureRules.LAYERS;

    @ArchTest
    static final ArchRule adaptersIndependent = ArchitectureRules.ADAPTERS_INDEPENDENT;

    @ArchTest
    static final ArchRule coreFrameworkFree = ArchitectureRules.CORE_FRAMEWORK_FREE;

    @ArchTest
    static final ArchRule mappingOnlyInEntities = ArchitectureRules.MAPPING_ONLY_IN_ENTITIES;

    @ArchTest
    static final ArchRule portsArePlain = ArchitectureRules.PORTS_ARE_PLAIN;

    @ArchTest
    static final ArchRule transactionsInServices = ArchitectureRules.TRANSACTIONS_IN_SERVICES;

    @ArchTest
    static final ArchRule noAmbientTimeOrIds = ArchitectureRules.NO_AMBIENT_TIME_OR_IDS;

    @ArchTest
    static final ArchRule noSpringComponentsInCore = ArchitectureRules.NO_SPRING_COMPONENTS_IN_CORE;

    @ArchTest
    static final ArchRule apiUsesOnlyUseCases = ArchitectureRules.API_USES_ONLY_USE_CASES;
}
