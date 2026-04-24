package com.mx.cryptomonitor.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayBaselineMigrationIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15")
          .withDatabaseName("crypto_portfolio_flyway_test")
          .withUsername("test")
          .withPassword("test");

  @Test
  void shouldApplyBaselineSchemaOnFreshDatabase() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .load();

    MigrateResult result = flyway.migrate();

    assertThat(result.success).isTrue();
    assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(3);

    try (Connection connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      assertThat(tableExists(connection, "users")).isTrue();
      assertThat(tableExists(connection, "audit_logs")).isTrue();
      assertThat(tableExists(connection, "portfolio_entry")).isTrue();
      assertThat(tableExists(connection, "transaction")).isTrue();
      assertThat(tableExists(connection, "flyway_schema_history")).isTrue();
      assertThat(indexExists(connection, "idx_transaction_user_date")).isTrue();
      assertThat(indexExists(connection, "idx_audit_logs_user_timestamp")).isTrue();
      assertThat(indexExists(connection, "uq_users_email_lower")).isTrue();
      assertThat(schemaHistoryContains(connection, "1")).isTrue();
      assertThat(schemaHistoryContains(connection, "2")).isTrue();
      assertThat(schemaHistoryContains(connection, "3")).isTrue();
      assertThat(schemaHistoryContains(connection, "2026.02.18.01")).isTrue();
    }
  }

  private boolean tableExists(Connection connection, String tableName) throws SQLException {
    String sql =
        "select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = '%s')"
            .formatted(tableName);
    try (Statement statement = connection.createStatement();
        var resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getBoolean(1);
    }
  }

  private boolean indexExists(Connection connection, String indexName) throws SQLException {
    String sql =
        "select exists (select 1 from pg_indexes where schemaname = 'public' and indexname = '%s')"
            .formatted(indexName);
    try (Statement statement = connection.createStatement();
        var resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getBoolean(1);
    }
  }

  private boolean schemaHistoryContains(Connection connection, String version) throws SQLException {
    String sql =
        "select exists (select 1 from flyway_schema_history where version = '%s' and success = true)"
            .formatted(version);
    try (Statement statement = connection.createStatement();
        var resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getBoolean(1);
    }
  }
}
