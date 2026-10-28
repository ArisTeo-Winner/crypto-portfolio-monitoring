package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * TEST 2b — verifica el estado post-migración de la columna asset_type en la tabla transaction.
 * Corre Flyway completo sobre Postgres real, luego consulta information_schema y datos.
 */
@Testcontainers(disabledWithoutDocker = true)
class AssetTypeDataMigrationIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @BeforeAll
  static void applyMigrations() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .validateOnMigrate(true)
        .load()
        .migrate();
  }

  private Connection connection() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private int queryInt(String sql) throws Exception {
    try (Connection conn = connection();
        ResultSet rs = conn.createStatement().executeQuery(sql)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  @Test
  void noLegacyBonosOrIndiceRowsExistAfterMigration() throws Exception {
    int count = queryInt("SELECT COUNT(*) FROM transaction WHERE asset_type IN ('BONOS','INDICE')");
    assertThat(count)
        .as("Ninguna fila debe tener asset_type='BONOS' o 'INDICE' tras la migración")
        .isZero();
  }

  @Test
  void allExistingTransactionRowsUseApprovedAssetTypes() throws Exception {
    // En DB limpia siempre es 0, pero este query detectaría regresiones si hay datos de prueba.
    int invalid =
        queryInt(
            "SELECT COUNT(*) FROM transaction"
                + " WHERE asset_type NOT IN"
                + " ('CRYPTO','STOCK','ETF','GOVERNMENT_BOND','CORPORATE_BOND','INDEX','FOREX')");
    assertThat(invalid)
        .as("Todos los valores de asset_type deben ser del conjunto aprobado")
        .isZero();
  }

  @Test
  void dividendDetailCheckConstraintRejectsInvalidDividendType() throws Exception {
    int checkExists =
        queryInt(
            "SELECT COUNT(*)"
                + " FROM information_schema.check_constraints cc"
                + " JOIN information_schema.table_constraints tc"
                + "   ON cc.constraint_name = tc.constraint_name"
                + " WHERE tc.table_name = 'dividend_detail'"
                + "   AND cc.check_clause LIKE '%CASH%'");
    assertThat(checkExists)
        .as("CHECK constraint (CASH, STOCK) en dividend_detail.dividend_type debe existir")
        .isGreaterThan(0);
  }

  @Test
  void dividendDetailDefaultDividendTypeIsCash() throws Exception {
    String defaultVal = null;
    try (Connection conn = connection();
        ResultSet rs =
            conn.createStatement()
                .executeQuery(
                    "SELECT column_default FROM information_schema.columns"
                        + " WHERE table_name='dividend_detail'"
                        + " AND column_name='dividend_type'")) {
      if (rs.next()) {
        defaultVal = rs.getString(1);
      }
    }
    assertThat(defaultVal)
        .as("dividend_detail.dividend_type debe tener DEFAULT 'CASH'")
        .isNotNull()
        .containsIgnoringCase("CASH");
  }

  @Test
  void dividendDetailUniqueConstraintRejectsDuplicateTransactionId() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    try (Connection conn = connection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "INSERT INTO users (id, username, email, created_at, updated_at)"
              + " VALUES ('"
              + userId
              + "', 'diviuniqueuser', 'divi-unique@example.com', now(), now())");
      stmt.execute(
          "INSERT INTO transaction (transaction_id, user_id, portfolio_entry_id, asset_symbol,"
              + " asset_type, transaction_type, quantity, price_per_unit, total_value,"
              + " created_at, updated_at)"
              + " VALUES ('"
              + transactionId
              + "', '"
              + userId
              + "', '"
              + UUID.randomUUID()
              + "', 'AAPL', 'STOCK', 'DIVIDEND', 0, 0, 50, now(), now())");
      stmt.execute("INSERT INTO dividend_detail (transaction_id) VALUES ('" + transactionId + "')");

      assertThatThrownBy(
              () ->
                  stmt.execute(
                      "INSERT INTO dividend_detail (transaction_id) VALUES ('"
                          + transactionId
                          + "')"))
          .as(
              "Un segundo dividend_detail con el mismo transaction_id debe violar la constraint UNIQUE")
          .isInstanceOf(java.sql.SQLException.class);
    }
  }

  @Test
  void legacyFuturesRowsAreMigratedToStock() throws Exception {
    String schema = "futures_migration_test";

    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas(schema)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("2026_06_14_05"))
        .load()
        .migrate();

    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    try (Connection conn = connection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SET search_path TO " + schema);
      stmt.execute(
          "INSERT INTO users (id, username, email, created_at, updated_at)"
              + " VALUES ('"
              + userId
              + "', 'legacyfuturesuser', 'legacy-futures@example.com', now(), now())");
      stmt.execute(
          "INSERT INTO transaction (transaction_id, user_id, portfolio_entry_id, asset_symbol,"
              + " asset_type, transaction_type, quantity, price_per_unit, total_value,"
              + " created_at, updated_at)"
              + " VALUES ('"
              + transactionId
              + "', '"
              + userId
              + "', '"
              + UUID.randomUUID()
              + "', 'ES', 'FUTURES', 'BUY', 1, 100, 100, now(), now())");
    }

    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas(schema)
        .locations("classpath:db/migration")
        .validateOnMigrate(true)
        .load()
        .migrate();

    try (Connection conn = connection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SET search_path TO " + schema);
      try (ResultSet rs =
          stmt.executeQuery(
              "SELECT asset_type FROM transaction WHERE transaction_id = '"
                  + transactionId
                  + "'")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1))
            .as("Fila legacy FUTURES debe migrarse a un AssetType valido")
            .isEqualTo("STOCK");
      }
    }
  }
}
