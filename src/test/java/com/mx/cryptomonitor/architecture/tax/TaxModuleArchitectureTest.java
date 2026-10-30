package com.mx.cryptomonitor.architecture.tax;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.mx.cryptomonitor",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class TaxModuleArchitectureTest {

  @ArchTest
  static final ArchRule tax_controllers_should_live_in_inbound_rest =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .and()
          .resideInAPackage("..tax..")
          .should()
          .resideInAPackage("..tax.infrastructure.inbound.rest..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule tax_services_should_live_in_application_service =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .and()
          .resideInAPackage("..tax..")
          .should()
          .resideInAPackage("..tax.application.service..");

  @ArchTest
  static final ArchRule tax_ports_should_live_in_application_port =
      classes()
          .that()
          .haveSimpleNameEndingWith("Port")
          .and()
          .resideInAPackage("..tax..")
          .should()
          .resideInAPackage("..tax.application.port..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule tax_domain_should_not_depend_on_tax_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..tax.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..tax.infrastructure..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule tax_application_should_not_depend_on_tax_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..tax.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..tax.infrastructure..")
          .allowEmptyShould(true);

  /** El dominio fiscal es puro: no conoce otros módulos ni Spring. */
  @ArchTest
  static final ArchRule tax_domain_should_not_depend_on_other_modules =
      noClasses()
          .that()
          .resideInAnyPackage("..tax.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..portfolio..", "..marketdata..", "..transaction..", "..user..");

  /** tax consume portfolio solo por su API pública (port.in), nunca dominio/infra internos. */
  @ArchTest
  static final ArchRule tax_must_reach_portfolio_only_through_ports =
      noClasses()
          .that()
          .resideInAnyPackage("..tax..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..portfolio.domain..",
              "..portfolio.infrastructure..",
              "..portfolio.application.service..");

  @ArchTest
  static final ArchRule tax_module_packages_should_be_cycle_free =
      slices().matching("com.mx.cryptomonitor.tax.(*)..").should().beFreeOfCycles();
}
