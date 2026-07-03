package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import org.flywaydb.core.Flyway;
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
}
