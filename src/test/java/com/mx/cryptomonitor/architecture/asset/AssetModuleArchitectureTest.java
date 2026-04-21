package com.mx.cryptomonitor.architecture.asset;

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
class AssetModuleArchitectureTest {

  @ArchTest
  static final ArchRule asset_controllers_should_live_in_inbound_rest =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .and()
          .resideInAPackage("..asset..")
          .should()
          .resideInAPackage("..asset.infrastructure.inbound.rest..");

  @ArchTest
  static final ArchRule asset_services_should_live_in_application_service =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .and()
          .resideInAPackage("..asset..")
          .should()
          .resideInAPackage("..asset.application.service..");

  @ArchTest
  static final ArchRule asset_responses_should_live_in_application_dto_response =
      classes()
          .that()
          .haveSimpleNameEndingWith("Response")
          .and()
          .resideInAPackage("..asset..")
          .should()
          .resideInAPackage("..asset.application.dto.response..");

  @ArchTest
  static final ArchRule asset_exception_handlers_should_live_in_problem_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("ExceptionHandler")
          .and()
          .resideInAPackage("..asset..")
          .should()
          .resideInAPackage("..asset.infrastructure.inbound.rest.problem..");

  @ArchTest
  static final ArchRule asset_application_should_not_depend_on_asset_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..asset.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..asset.infrastructure..");

  @ArchTest
  static final ArchRule asset_module_packages_should_be_cycle_free =
      slices().matching("com.mx.cryptomonitor.asset.(*)..").should().beFreeOfCycles();
}
