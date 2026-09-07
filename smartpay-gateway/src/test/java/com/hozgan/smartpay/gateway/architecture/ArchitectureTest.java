package com.hozgan.smartpay.gateway.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@Tag("unit")
@DisplayName("smartpay-gateway — Architecture Fitness Tests")
class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hozgan.smartpay.gateway");
    }

    @Test
    @DisplayName("All REST controllers must reside in the .web package")
    void controllersMustResideInWebPackage() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..web..")
                .allowEmptyShould(true)
                .because("All HTTP endpoints and REST controllers must reside in the .web package per AGENTS.md");
    }

    @Test
    @DisplayName("Constructor injection is mandatory (no field injection)")
    void noFieldInjection() {
        ArchRule rule = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
                .because("Spring Boot enforces constructor injection for immutability and testability");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("No synchronized methods allowed (prevents Project Loom carrier thread pinning)")
    void noSynchronizedMethods() {
        ArchRule rule = noMethods()
                .should().haveModifier(JavaModifier.SYNCHRONIZED)
                .because("Synchronized methods cause carrier thread pinning in Project Loom virtual threads");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("No standard streams (System.out/System.err) - use SLF4J structured logging")
    void noStandardStreams() {
        ArchRule rule = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                .because("Production code must use SLF4J structured logging");

        rule.check(importedClasses);
    }
}
