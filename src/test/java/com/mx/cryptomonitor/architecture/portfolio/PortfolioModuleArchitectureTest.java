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

  // -------------------------------------------------------------------------
  // Historical engine domain purity — enforces the "Historical engine fully
  // validated" structural contract required before frontend integration.
  // -------------------------------------------------------------------------

  /**
   * The history use-case service must never reach across the hexagonal boundary and import
   * infrastructure adapter classes directly. All external data must flow through the ports defined
   * in application.port.out.
   */
  @ArchTest
  static final ArchRule history_service_must_not_depend_on_outbound_adapters =
      noClasses()
          .that()
          .haveSimpleName("GetPortfolioTotalHistoryService")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..portfolio.infrastructure.outbound..");

  /**
   * The history use-case service must never depend on controllers or inbound infrastructure. This
   * would invert the dependency direction.
   */
  @ArchTest
  static final ArchRule history_service_must_not_depend_on_controllers =
      noClasses()
          .that()
          .haveSimpleName("GetPortfolioTotalHistoryService")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..portfolio.infrastructure.inbound..");

  /**
   * The history use-case service must not import WebClient or any reactive HTTP client directly.
   * All HTTP communication is delegated to adapters via ports.
   */
  @ArchTest
  static final ArchRule history_service_must_not_use_webclient_directly =
      noClasses()
          .that()
          .haveSimpleName("GetPortfolioTotalHistoryService")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.web.reactive.function.client.WebClient");

  /**
   * Domain engines (pure computation) must be isolated from all infrastructure, market-data module
   * internals, and shared Spring components. They must depend only on the portfolio domain model.
   */
  @ArchTest
  static final ArchRule portfolio_domain_engines_must_be_pure =
      noClasses()
          .that()
          .resideInAPackage("..portfolio.domain.engine..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..portfolio.infrastructure..",
              "..portfolio.application.service..",
              "..marketdata..",
              "org.springframework.web..");

  /**
   * Application-layer classes must not bypass the port abstraction and import infrastructure
   * adapter classes from the market-data module.
   */
  @ArchTest
  static final ArchRule portfolio_application_must_not_reach_marketdata_infrastructure =
      noClasses()
          .that()
          .resideInAPackage("..portfolio.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..marketdata.infrastructure..");
}
