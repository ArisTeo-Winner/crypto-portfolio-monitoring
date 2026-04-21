package com.mx.cryptomonitor.architecture.portfolio;

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
class PortfolioModuleArchitectureTest {

  @ArchTest
  static final ArchRule portfolio_controllers_should_live_in_inbound_rest =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .and()
          .resideInAPackage("..portfolio..")
          .should()
          .resideInAPackage("..portfolio.infrastructure.inbound.rest..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule portfolio_services_should_live_in_application_service =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .and()
          .resideInAPackage("..portfolio..")
          .should()
          .resideInAPackage("..portfolio.application.service..");

  @ArchTest
  static final ArchRule portfolio_ports_should_live_in_application_port =
      classes()
          .that()
          .haveSimpleNameEndingWith("Port")
          .and()
          .resideInAPackage("..portfolio..")
          .should()
          .resideInAPackage("..portfolio.application.port..");

  @ArchTest
  static final ArchRule portfolio_repositories_should_live_in_domain_repository =
      classes()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .and()
          .resideInAPackage("..portfolio..")
          .should()
          .resideInAPackage("..portfolio.domain.repository..");

  @ArchTest
  static final ArchRule portfolio_entities_should_live_in_domain_model =
      classes()
          .that()
          .areAnnotatedWith("jakarta.persistence.Entity")
          .and()
          .resideInAPackage("..portfolio..")
          .should()
          .resideInAPackage("..portfolio.domain.model..");

  @ArchTest
  static final ArchRule portfolio_domain_should_not_depend_on_portfolio_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..portfolio.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..portfolio.infrastructure..");

  @ArchTest
  static final ArchRule portfolio_application_should_not_depend_on_portfolio_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..portfolio.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..portfolio.infrastructure..");

  @ArchTest
  static final ArchRule portfolio_services_should_access_marketdata_only_through_ports =
      noClasses()
          .that()
          .resideInAnyPackage("..portfolio.application.service..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..marketdata.domain..",
              "..marketdata.infrastructure..",
              "..marketdata.application.service..");

  @ArchTest
  static final ArchRule portfolio_module_packages_should_be_cycle_free =
      slices().matching("com.mx.cryptomonitor.portfolio.(*)..").should().beFreeOfCycles();
}
