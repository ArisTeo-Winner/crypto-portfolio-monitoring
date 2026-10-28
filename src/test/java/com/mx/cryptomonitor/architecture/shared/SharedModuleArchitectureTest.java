package com.mx.cryptomonitor.architecture.shared;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.mx.cryptomonitor",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class SharedModuleArchitectureTest {

  @ArchTest
  static final ArchRule shared_configs_should_live_in_shared_infrastructure_config =
      classes()
          .that()
          .haveSimpleNameEndingWith("Config")
          .and()
          .resideInAPackage("..shared..")
          .should()
          .resideInAPackage("..shared.infrastructure.config..");

  @ArchTest
  static final ArchRule redis_first_should_be_restricted_to_shared_cache_and_user_token_store =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "..shared.infrastructure.config..",
              "..user.application.service..",
              "..shared.infrastructure.security.ratelimit..",
              "..portfolio.infrastructure.outbound.redis..",
              "..marketdata.infrastructure.outbound..",
              "..asset.infrastructure.outbound.redis..")
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(StringRedisTemplate.class, RedisConnectionFactory.class);

  @ArchTest
  static final ArchRule shared_module_packages_should_be_cycle_free =
      slices().matching("com.mx.cryptomonitor.shared.(*)..").should().beFreeOfCycles();
}
