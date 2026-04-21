package com.mx.cryptomonitor.architecture.transaction;

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
class TransactionModuleArchitectureTest {

  @ArchTest
  static final ArchRule transaction_controllers_should_live_in_inbound_rest =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .and()
          .resideInAPackage("..transaction..")
          .should()
          .resideInAPackage("..transaction.infrastructure.inbound.rest..");

  @ArchTest
  static final ArchRule transaction_services_should_live_in_application_service =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .and()
          .resideInAPackage("..transaction..")
          .should()
          .resideInAPackage("..transaction.application.service..");

  @ArchTest
  static final ArchRule transaction_mappers_should_live_in_application_mapper =
      classes()
          .that()
          .haveSimpleNameEndingWith("Mapper")
          .and()
          .resideInAPackage("..transaction..")
          .should()
          .resideInAPackage("..transaction.application.mapper..");

  @ArchTest
  static final ArchRule transaction_repositories_should_live_in_domain_repository =
      classes()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .and()
          .resideInAPackage("..transaction..")
          .should()
          .resideInAPackage("..transaction.domain.repository..");

  @ArchTest
  static final ArchRule transaction_ports_should_live_in_application_port =
      classes()
          .that()
          .haveSimpleNameEndingWith("Port")
          .and()
          .resideInAPackage("..transaction..")
          .should()
          .resideInAPackage("..transaction.application.port..");

  @ArchTest
  static final ArchRule transaction_outbound_adapters_should_live_in_infrastructure_outbound =
      classes()
          .that()
          .haveSimpleNameEndingWith("Adapter")
          .and()
          .resideInAPackage("..transaction..")
          .should()
          .resideInAPackage("..transaction.infrastructure.outbound..");

  @ArchTest
  static final ArchRule transaction_entities_should_live_in_domain_model =
      classes()
          .that()
          .areAnnotatedWith("jakarta.persistence.Entity")
          .and()
          .resideInAPackage("..transaction..")
          .should()
          .resideInAPackage("..transaction.domain.model..");

  @ArchTest
  static final ArchRule transaction_domain_should_not_depend_on_transaction_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..transaction.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..transaction.infrastructure..");

  @ArchTest
  static final ArchRule transaction_application_should_not_depend_on_transaction_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..transaction.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..transaction.infrastructure..");

  @ArchTest
  static final ArchRule transaction_input_ports_should_be_interfaces =
      classes()
          .that()
          .resideInAPackage("..transaction.application.port.in..")
          .should()
          .beInterfaces();

  @ArchTest
  static final ArchRule transaction_inbound_should_not_depend_on_application_services =
      noClasses()
          .that()
          .resideInAnyPackage("..transaction.infrastructure.inbound..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..transaction.application.service..");

  @ArchTest
  static final ArchRule transaction_application_should_not_depend_on_legacy_portfolio_service =
      noClasses()
          .that()
          .resideInAnyPackage("..transaction.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..domain.services..");

  @ArchTest
  static final ArchRule transaction_should_not_depend_on_portfolio_repositories =
      noClasses()
          .that()
          .resideInAnyPackage("..transaction..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..portfolio.domain.repository..");

  @ArchTest
  static final ArchRule transaction_domain_should_not_depend_on_portfolio_module =
      noClasses()
          .that()
          .resideInAnyPackage("..transaction.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..portfolio..");

  @ArchTest
  static final ArchRule transaction_should_not_depend_on_portfolio_domain_model =
      noClasses()
          .that()
          .resideInAnyPackage("..transaction..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..portfolio.domain.model..");

  @ArchTest
  static final ArchRule non_transaction_modules_should_not_depend_on_transaction_inbound =
      noClasses()
          .that()
          .resideOutsideOfPackage("..transaction..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..transaction.infrastructure.inbound..");

  @ArchTest
  static final ArchRule transaction_module_packages_should_be_cycle_free =
      slices().matching("com.mx.cryptomonitor.transaction.(*)..").should().beFreeOfCycles();
}
