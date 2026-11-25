package com.barkwalk.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.barkwalk", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule web_must_not_depend_on_repositories =
            noClasses()
                    .that().resideInAPackage("com.barkwalk..web..")
                    .should().dependOnClassesThat().resideInAPackage("com.barkwalk..repository..")
                    .because("Controllers must call services, not repositories");

    @ArchTest
    static final ArchRule services_must_not_depend_on_web =
            noClasses()
                    .that().resideInAPackage("com.barkwalk..service..")
                    .should().dependOnClassesThat().resideInAPackage("com.barkwalk..web..")
                    .because("Services must not import presentation DTOs from web");
}
