package com.hozgan.smartpay.common.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.Tag;

@Tag("unit")
@DisplayName("Modern Java 25 & Spring Boot 4.1 Architecture Tests")
class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hozgan.smartpay.common");
    }

    @Nested
    @DisplayName("Modern Java: Records & Immutability Rules")
    class RecordsAndImmutabilityRules {

        @Test
        @DisplayName("Domain models must be Java records, enums, or interfaces for immutability")
        void domainModelsShouldBeRecordsOrEnumsOrInterfaces() {
            ArchCondition<JavaClass> beImmutableRecord = new ArchCondition<>("be Java records, enums, or interfaces") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    boolean isValid = javaClass.isRecord() || javaClass.isEnum() || javaClass.isInterface();
                    if (!isValid) {
                        String message = String.format("Class %s in model package must be a record, enum, or interface",
                                javaClass.getName());
                        events.add(SimpleConditionEvent.violated(javaClass, message));
                    }
                }
            };

            ArchRule rule = classes()
                    .that().resideInAPackage("..common.model..")
                    .should(beImmutableRecord)
                    .because("Modern Java domain models must be immutable records to ensure thread-safety");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Domain model records must not have setter methods")
        void domainModelsMustNotHaveSetters() {
            ArchRule rule = noMethods()
                    .that().areDeclaredInClassesThat().resideInAPackage("..common.model..")
                    .should().haveNameStartingWith("set")
                    .because("Domain model records are immutable and must not expose mutable setters");

            rule.check(importedClasses);
        }
        @Test
        @DisplayName("Model fields must be final and immutable")
        void modelFieldsMustBeFinal() {
            ArchRule rule = fields()
                    .that().areDeclaredInClassesThat().resideInAPackage("..common.model..")
                    .should().beFinal()
                    .because("All state in domain models must be strictly final");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Domain models must not be Spring managed beans (pure domain value objects)")
        void domainModelsMustNotBeSpringBeans() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..common.model..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Component")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                    .because("Domain models in DDD must remain pure Java records independent of Spring container");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Modern Java: Streams, Collections & Date/Time API Rules")
    class ModernJavaAPIRules {

        @Test
        @DisplayName("Legacy collection types (Vector, Hashtable, Stack) are forbidden")
        void noLegacyCollections() {
            ArchRule rule = noClasses()
                    .should().dependOnClassesThat().belongToAnyOf(
                            Vector.class,
                            Hashtable.class,
                            java.util.Stack.class
                    )
                    .because("Modern Java code must use modern collections and Stream API");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Legacy java.util.Date and java.util.Calendar are forbidden in favor of java.time.*")
        void noLegacyDateTime() {
            ArchRule rule = noClasses()
                    .should().dependOnClassesThat().belongToAnyOf(
                            Date.class,
                            Calendar.class
                    )
                    .because("Modern Java code must use java.time (JSR-310) instead of legacy Date/Calendar");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Generic exception throwing is forbidden")
        void noGenericExceptions() {
            ArchRule rule = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS
                    .because("Specific domain or standard exceptions must be thrown for clear error semantics");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Virtual Threads & Concurrency Rules (Project Loom)")
    class VirtualThreadsAndConcurrencyRules {

        @Test
        @DisplayName("Classes must not directly extend java.lang.Thread or call new Thread()")
        void noDirectThreadCreation() {
            ArchRule rule = noClasses()
                    .should().accessClassesThat().areAssignableTo(Thread.class)
                    .andShould().beAssignableTo(Thread.class)
                    .because("Virtual threads should be managed via Thread.ofVirtual() or Executors.newVirtualThreadPerTaskExecutor()");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Methods should avoid 'synchronized' to prevent virtual thread carrier pinning")
        void noSynchronizedMethodsToPreventCarrierPinning() {
            ArchCondition<JavaMethod> notBeSynchronized = new ArchCondition<>("not be declared with synchronized modifier") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    if (method.getModifiers().contains(JavaModifier.SYNCHRONIZED)) {
                        String message = String.format("Method %s is synchronized, which can pin Virtual Thread carrier threads. Use ReentrantLock or immutable records instead.",
                                method.getFullName());
                        events.add(SimpleConditionEvent.violated(method, message));
                    }
                }
            };

            ArchRule rule = methods()
                    .should(notBeSynchronized)
                    .because("Synchronized blocks/methods pin carrier threads in Project Loom (Virtual Threads). Prefer Lock or immutable records.");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("No classes should access deprecated Thread methods (Thread.stop, suspend, resume)")
        void noDeprecatedThreadMethods() {
            ArchRule rule = noClasses()
                    .should().callMethod(Thread.class, "stop")
                    .orShould().callMethod(Thread.class, "suspend")
                    .orShould().callMethod(Thread.class, "resume")
                    .because("Deprecated Thread methods are unsafe and strictly forbidden with Virtual Threads");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Spring Boot 4.1 & Clean Architecture Rules")
    class SpringBootAndCleanArchitectureRules {

        @Test
        @DisplayName("No classes should use field injection (constructor injection is mandatory)")
        void noFieldInjection() {
            ArchRule rule = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
                    .because("Spring Boot 4.1 enforces constructor injection for immutability and testability");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("No classes should use standard streams (System.out / System.err) - use structured logging")
        void noStandardStreams() {
            ArchRule rule = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                    .because("Production code must use SLF4J structured logging instead of System.out/System.err");
            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Domain model package must be independent of web, persistence, or controller layers")
        void domainModelMustBeIndependent() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..common.model..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "..common.model..",
                            "..common.util..",
                            "java..",
                            "javax..",
                            "org.jspecify..",
                            "org.javamoney.."
                    )
                    .because("Domain models must remain pure value objects independent of framework infrastructure");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Packages must be free of cyclic dependencies")
        void noCyclicDependencies() {
            ArchRule rule = slices().matching("com.hozgan.smartpay.common.(*)..")
                    .should().beFreeOfCycles()
                    .because("Clean architecture requires an acyclic package dependency graph");

            rule.check(importedClasses);
        }
    }
}
