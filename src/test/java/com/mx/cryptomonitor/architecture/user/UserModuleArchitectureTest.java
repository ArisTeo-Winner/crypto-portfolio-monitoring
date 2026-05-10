package com.mx.cryptomonitor.architecture.user;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.mx.cryptomonitor",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class UserModuleArchitectureTest {

  @ArchTest
  static final ArchRule controllers_in_user_module_should_live_in_inbound_rest =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .and()
          .resideInAPackage("..user..")
          .should()
          .resideInAPackage("..user.infrastructure.inbound.rest..");

  @ArchTest
  static final ArchRule exception_handlers_in_user_module_should_live_in_problem_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("ExceptionHandler")
          .and()
          .resideInAPackage("..user..")
          .should()
          .resideInAPackage("..user.infrastructure.inbound.rest.problem..");

  @ArchTest
  static final ArchRule user_inbound_adapter_should_not_depend_on_application_controller_layer =
      noClasses()
          .that()
          .resideInAnyPackage("..user.infrastructure.inbound..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application.controllers..");

  @ArchTest
  static final ArchRule non_user_modules_should_not_depend_on_user_inbound_adapter =
      noClasses()
          .that()
          .resideOutsideOfPackage("..user..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..user.infrastructure.inbound..");

  @ArchTest
  static final ArchRule user_domain_should_not_depend_on_user_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..user.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..user.infrastructure..");

  @ArchTest
  static final ArchRule user_application_should_not_depend_on_user_infrastructure =
      noClasses()
          .that()
          .resideInAnyPackage("..user.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..user.infrastructure..");

  @ArchTest
  static final ArchRule only_redis_outbound_adapter_should_use_string_redis_template =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "..user.application.service..",
              "..shared.infrastructure.security.ratelimit..",
              "..portfolio.infrastructure.outbound.redis..",
              "..marketdata.infrastructure.outbound..")
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(StringRedisTemplate.class);

  @ArchTest
  static final ArchRule refresh_token_repository_should_not_be_used_outside_repository_package =
      noClasses()
          .that()
          .resideOutsideOfPackage("..user.domain.repository..")
          .should()
          .dependOnClassesThat()
          .haveSimpleName("RefreshTokenRepository");

  @ArchTest
  static final ArchRule user_application_should_depend_on_token_port_not_jwt_adapter =
      noClasses()
          .that()
          .resideInAnyPackage("..user.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..user.infrastructure.security..");

  @ArchTest
  static final ArchRule user_request_should_live_in_application_dto_request =
      classes()
          .that()
          .haveSimpleNameEndingWith("Request")
          .and()
          .resideInAPackage("..user..")
          .should()
          .resideInAnyPackage("..user.application.dto.request..");

  @ArchTest
  static final ArchRule user_response_should_live_in_application_dto_response =
      classes()
          .that()
          .haveSimpleNameEndingWith("Response")
          .and()
          .resideInAPackage("..user..")
          .should()
          .resideInAnyPackage("..user.application.dto.response..");

  @ArchTest
  static final ArchRule user_mapper_should_live_in_application_mapper =
      classes()
          .that()
          .haveSimpleNameEndingWith("Mapper")
          .and()
          .resideInAPackage("..user..")
          .should()
          .resideInAPackage("..user.application.mapper..");

  @ArchTest
  static final ArchRule user_service_should_live_in_application_service =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .and()
          .resideInAPackage("..user.application..")
          .should()
          .resideInAPackage("..user.application.service..");

  @ArchTest
  static final ArchRule user_repository_should_live_in_domain_repository =
      classes()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .and()
          .resideInAPackage("..user..")
          .should()
          .resideInAPackage("..user.domain.repository..");

  @ArchTest
  static final ArchRule user_entities_should_live_in_domain_model =
      classes()
          .that()
          .areAnnotatedWith("jakarta.persistence.Entity")
          .and()
          .resideInAPackage("..user..")
          .should()
          .resideInAPackage("..user.domain.model..");

  @ArchTest
  static final ArchRule user_module_packages_should_be_cycle_free =
      slices().matching("com.mx.cryptomonitor.user.(*)..").should().beFreeOfCycles();
}
