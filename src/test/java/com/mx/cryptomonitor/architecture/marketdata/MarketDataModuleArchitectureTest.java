package com.mx.cryptomonitor.architecture.marketdata;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.mx.cryptomonitor.marketdata",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class MarketDataModuleArchitectureTest {

  @ArchTest
  static final ArchRule marketdata_controllers_should_live_in_inbound_rest =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .and()
          .resideInAnyPackage("com.mx.cryptomonitor.marketdata..")
          .should()
          .resideInAPackage("..marketdata.infrastructure.inbound.rest..");

  @ArchTest
  static final ArchRule marketdata_exception_handlers_should_live_in_problem_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("ExceptionHandler")
          .and()
          .resideInAnyPackage("com.mx.cryptomonitor.marketdata..")
          .should()
          .resideInAPackage("..marketdata.infrastructure.inbound.rest.problem..");

  @ArchTest
  static final ArchRule marketdata_services_should_live_in_application_service =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .and()
          .resideInAnyPackage("com.mx.cryptomonitor.marketdata..")
          .should()
          .resideInAPackage("..marketdata.application.service..");

  @ArchTest
  static final ArchRule marketdata_ports_should_live_in_application_port =
      classes()
          .that()
          .haveSimpleNameEndingWith("Port")
          .and()
          .resideInAnyPackage("com.mx.cryptomonitor.marketdata..")
          .should()
          .resideInAPackage("..marketdata.application.port..");

  @ArchTest
  static final ArchRule marketdata_adapters_should_live_in_infrastructure_outbound =
      classes()
          .that()
          .haveSimpleNameEndingWith("Adapter")
          .and()
          .resideInAnyPackage("com.mx.cryptomonitor.marketdata..")
          .should()
          .resideInAPackage("..marketdata.infrastructure.outbound..");

  @ArchTest
  static final ArchRule marketdata_configs_should_live_in_infrastructure_configuration =
      classes()
          .that()
          .haveSimpleNameEndingWith("Config")
          .and()
          .resideInAnyPackage("com.mx.cryptomonitor.marketdata..")
          .should()
          .resideInAPackage("..marketdata.infrastructure.configuration..");

  @ArchTest
  static final ArchRule marketdata_domain_should_not_depend_on_marketdata_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..marketdata.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..marketdata.infrastructure..");

  @ArchTest
  static final ArchRule marketdata_application_should_not_depend_on_marketdata_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..marketdata.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..marketdata.infrastructure..");

  @ArchTest
  static final ArchRule marketdata_module_packages_should_be_cycle_free =
      slices().matching("com.mx.cryptomonitor.marketdata.(*)..").should().beFreeOfCycles();
}
