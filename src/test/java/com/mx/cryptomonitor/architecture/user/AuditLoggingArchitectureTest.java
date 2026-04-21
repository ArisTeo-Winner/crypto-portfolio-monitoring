package com.mx.cryptomonitor.architecture.user;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.mx.cryptomonitor",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class AuditLoggingArchitectureTest {

  @ArchTest
  static final ArchRule audit_log_entity_should_live_in_user_domain_model =
      classes()
          .that()
          .haveSimpleName("AuditLog")
          .should()
          .resideInAPackage("..user.domain.model..");

  @ArchTest
  static final ArchRule audit_event_type_should_live_in_user_domain_model =
      classes()
          .that()
          .haveSimpleName("AuditEventType")
          .should()
          .resideInAPackage("..user.domain.model..");

  @ArchTest
  static final ArchRule audit_log_repository_should_live_in_user_domain_repository =
      classes()
          .that()
          .haveSimpleName("AuditLogRepository")
          .should()
          .resideInAPackage("..user.domain.repository..");

  @ArchTest
  static final ArchRule audit_log_service_should_live_in_user_application_service =
      classes()
          .that()
          .haveSimpleName("AuditLogService")
          .should()
          .resideInAPackage("..user.application.service..");

  @ArchTest
  static final ArchRule audit_log_persistence_port_should_live_in_user_application_port_out =
      classes()
          .that()
          .haveSimpleName("AuditLogPersistencePort")
          .should()
          .resideInAPackage("..user.application.port.out..");

  @ArchTest
  static final ArchRule audit_log_persistence_adapter_should_live_in_user_infrastructure_outbound =
      classes()
          .that()
          .haveSimpleName("AuditLogPersistenceAdapter")
          .should()
          .resideInAPackage("..user.infrastructure.outbound..");

  @ArchTest
  static final ArchRule application_layer_should_not_depend_on_audit_log_repository =
      noClasses()
          .that()
          .resideInAnyPackage("..user.application..")
          .should()
          .dependOnClassesThat()
          .haveSimpleName("AuditLogRepository");

  @ArchTest
  static final ArchRule inbound_adapters_should_not_depend_on_audit_log_repository =
      noClasses()
          .that()
          .resideInAnyPackage("..user.infrastructure.inbound..")
          .should()
          .dependOnClassesThat()
          .haveSimpleName("AuditLogRepository");
}
