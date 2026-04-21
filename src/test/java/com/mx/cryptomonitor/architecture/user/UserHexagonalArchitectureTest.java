package com.mx.cryptomonitor.architecture.user;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.mx.cryptomonitor.user",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class UserHexagonalArchitectureTest {

  @ArchTest
  static final ArchRule user_hexagonal_layers_should_be_respected =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy("..user.domain..")
          .layer("Application")
          .definedBy("..user.application..")
          .layer("Infrastructure")
          .definedBy("..user.infrastructure..")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Infrastructure")
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Infrastructure")
          .whereLayer("Infrastructure")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Domain")
          .mayNotAccessAnyLayer()
          .whereLayer("Application")
          .mayOnlyAccessLayers("Domain")
          .whereLayer("Infrastructure")
          .mayOnlyAccessLayers("Application", "Domain");

  @ArchTest
  static final ArchRule domain_ports_should_be_interfaces =
      classes().that().resideInAPackage("..user.domain.port..").should().beInterfaces();

  @ArchTest
  static final ArchRule domain_ports_should_not_depend_on_application_or_infrastructure =
      noClasses()
          .that()
          .resideInAPackage("..user.domain.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..user.application..", "..user.infrastructure..");

  @ArchTest
  static final ArchRule core_domain_should_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAnyPackage(
              "..user.domain.model..", "..user.domain.exception..", "..user.domain.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..");
}
