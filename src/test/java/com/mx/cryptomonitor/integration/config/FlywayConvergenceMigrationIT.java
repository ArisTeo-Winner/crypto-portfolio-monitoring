package com.mx.cryptomonitor.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayConvergenceMigrationIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15")
          .withDatabaseName("crypto_portfolio_flyway_convergence_test")
          .withUsername("test")
          .withPassword("test");

  @BeforeEach
  void resetDatabase() throws SQLException {
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS public CASCADE");
      statement.execute("CREATE SCHEMA public");
    }
  }

  @Test
  void shouldBaselineExistingSchemaAndApplyV2ConvergenceIndexes() throws Exception {
    populateLegacySchema();

    flyway().migrate();

    try (Connection connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      assertThat(indexExists(connection, "uq_users_email_lower")).isTrue();
      assertThat(indexExists(connection, "idx_transaction_user_date")).isTrue();
      assertThat(indexExists(connection, "idx_portfolio_entry_user_asset_type")).isTrue();
      assertThat(schemaHistoryContains(connection, "2")).isTrue();
    }
  }

  @Test
  void shouldFailConvergenceWhenCaseInsensitiveDuplicateEmailsExist() throws Exception {
    populateLegacySchema();

    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      String now = LocalDateTime.now().toString().replace('T', ' ');
      statement.executeUpdate(
          "insert into users (id, username, email, password_hash, created_at, updated_at) values "
              + "('%s', 'user-one', 'test@example.com', 'hash-1', '%s', '%s')"
                  .formatted(UUID.randomUUID(), now, now));
      statement.executeUpdate(
          "insert into users (id, username, email, password_hash, created_at, updated_at) values "
              + "('%s', 'user-two', 'TEST@example.com', 'hash-2', '%s', '%s')"
                  .formatted(UUID.randomUUID(), now, now));
    }

    assertThatThrownBy(() -> flyway().migrate())
        .hasMessageContaining(
            "duplicate users.email values exist when compared case-insensitively");
  }

  private Flyway flyway() {
    return Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .baselineVersion("1")
        .cleanDisabled(true)
        .load();
  }

  private void populateLegacySchema() throws SQLException {
    DataSource dataSource = dataSource();
    ResourceDatabasePopulator populator =
        new ResourceDatabasePopulator(new ClassPathResource("sql/legacy-schema.sql"));
    populator.execute(dataSource);
  }

  private DataSource dataSource() {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setURL(postgres.getJdbcUrl());
    dataSource.setUser(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    return dataSource;
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
