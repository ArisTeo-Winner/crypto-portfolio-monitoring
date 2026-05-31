package com.mx.cryptomonitor.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifica que todas las migraciones Flyway se aplican y validan correctamente
 * contra una base de datos limpia, replicando el comportamiento de arranque en Docker.
 *
 * <p>Este test detecta:
 * <ul>
 *   <li>Errores de SQL en archivos de migración
 *   <li>Migraciones que no pueden aplicarse en orden
 *   <li>Discrepancias de checksum entre archivo local e historial de DB
 *   <li>Migraciones en estado FAILED o PENDING
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  private static Flyway flyway;

  @BeforeAll
  static void applyMigrations() {
    flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .load();

    flyway.migrate();
  }

  @Test
  void all_migrations_must_apply_without_failures() {
    long failed =
        Arrays.stream(flyway.info().all())
            .filter(m -> m.getState() == MigrationState.FAILED)
            .count();

    assertThat(failed)
        .as("No debe haber migraciones en estado FAILED")
        .isZero();
  }

  @Test
  void no_migration_must_remain_pending_after_startup() {
    long pending =
        Arrays.stream(flyway.info().all())
            .filter(m -> m.getState() == MigrationState.PENDING)
            .count();

    assertThat(pending)
        .as("Todas las migraciones deben estar aplicadas — ninguna en estado PENDING")
        .isZero();
  }

  @Test
  void flyway_validate_must_pass_after_applying_all_migrations() {
    // validate() lanza FlywayValidateException si algún checksum no coincide
    // con lo almacenado en flyway_schema_history — replica exactamente la
    // validación que Spring Boot ejecuta al arrancar en Docker.
    assertThatNoException()
        .as("flyway.validate() no debe lanzar excepción — los checksums deben coincidir")
        .isThrownBy(flyway::validate);
  }

  @Test
  void all_applied_migrations_must_have_registered_checksum() {
    MigrationInfo[] applied =
        Arrays.stream(flyway.info().all())
            .filter(m -> m.getState() == MigrationState.SUCCESS)
            .toArray(MigrationInfo[]::new);

    assertThat(applied)
        .as("Debe haber al menos una migración aplicada")
        .isNotEmpty();

    for (MigrationInfo info : applied) {
      assertThat(info.getChecksum())
          .as("La migración %s debe tener checksum registrado en flyway_schema_history",
              info.getVersion())
          .isNotNull();
    }
  }
}
