package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class TransactionSchemaMigrationIT {

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

  private int countColumns(String table, String column) throws Exception {
    try (Connection conn = connection();
        ResultSet rs =
            conn.createStatement()
                .executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_name='"
                        + table
                        + "' AND column_name='"
                        + column
                        + "'")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private String colAttr(String table, String column, String attr) throws Exception {
    try (Connection conn = connection();
        ResultSet rs =
            conn.createStatement()
                .executeQuery(
                    "SELECT "
                        + attr
                        + " FROM information_schema.columns"
                        + " WHERE table_name='"
                        + table
                        + "' AND column_name='"
                        + column
                        + "'")) {
      rs.next();
      return rs.getString(1);
    }
  }

  private Connection connection() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  // -------------------------------------------------------------------------
  // TEST 1a — nuevas columnas en transaction
  // -------------------------------------------------------------------------

  @Test
  void transactionTableHasAllNewColumns() throws Exception {
    for (String col :
        new String[] {
          "asset_name", "exchange", "broker", "currency",
          "face_value", "maturity_date", "coupon_rate", "auto_reinvestment"
        }) {
      assertThat(countColumns("transaction", col))
          .as("transaction.%s must exist", col)
          .isEqualTo(1);
    }
  }

  // -------------------------------------------------------------------------
  // TEST 1b — tipos y nullability
  // -------------------------------------------------------------------------

  @Test
  void assetNameIsNullableVarchar255() throws Exception {
    assertThat(colAttr("transaction", "asset_name", "data_type")).isEqualTo("character varying");
    assertThat(colAttr("transaction", "asset_name", "character_maximum_length")).isEqualTo("255");
    assertThat(colAttr("transaction", "asset_name", "is_nullable")).isEqualTo("YES");
  }

  @Test
  void currencyIsNullableVarchar3() throws Exception {
    assertThat(colAttr("transaction", "currency", "data_type")).isEqualTo("character varying");
    assertThat(colAttr("transaction", "currency", "character_maximum_length")).isEqualTo("3");
    assertThat(colAttr("transaction", "currency", "is_nullable")).isEqualTo("YES");
  }

  @Test
  void autoReinvestmentIsBooleanNotNullDefaultFalse() throws Exception {
    assertThat(colAttr("transaction", "auto_reinvestment", "data_type")).isEqualTo("boolean");
    assertThat(colAttr("transaction", "auto_reinvestment", "is_nullable")).isEqualTo("NO");
    assertThat(colAttr("transaction", "auto_reinvestment", "column_default"))
        .containsIgnoringCase("false");
  }

  // -------------------------------------------------------------------------
  // TEST 1c — tabla dividend_detail
  // -------------------------------------------------------------------------

  @Test
  void dividendDetailTableHasAllRequiredColumns() throws Exception {
    for (String col :
        new String[] {
          "id", "transaction_id", "ex_dividend_date", "dividend_type", "tax_withheld", "created_at"
        }) {
      assertThat(countColumns("dividend_detail", col))
          .as("dividend_detail.%s must exist", col)
          .isEqualTo(1);
    }
  }

  @Test
  void dividendDetailForeignKeyHasCascadeOnDelete() throws Exception {
    try (Connection conn = connection();
        ResultSet rs =
            conn.createStatement()
                .executeQuery(
                    "SELECT rc.delete_rule"
                        + " FROM information_schema.referential_constraints rc"
                        + " JOIN information_schema.table_constraints tc"
                        + "   ON rc.constraint_name = tc.constraint_name"
                        + " WHERE tc.table_name = 'dividend_detail'"
                        + "   AND tc.constraint_type = 'FOREIGN KEY'")) {
      assertThat(rs.next()).as("FK constraint on dividend_detail must exist").isTrue();
      assertThat(rs.getString("delete_rule")).isEqualTo("CASCADE");
    }
  }

  @Test
  void dividendDetailCheckConstraintOnDividendType() throws Exception {
    try (Connection conn = connection();
        ResultSet rs =
            conn.createStatement()
                .executeQuery(
                    "SELECT COUNT(*) FROM information_schema.check_constraints cc"
                        + " JOIN information_schema.table_constraints tc"
                        + "   ON cc.constraint_name = tc.constraint_name"
                        + " WHERE tc.table_name = 'dividend_detail'"
                        + "   AND cc.check_clause LIKE '%CASH%'")) {
      rs.next();
      assertThat(rs.getInt(1))
          .as("CHECK constraint on dividend_detail.dividend_type must exist")
          .isGreaterThan(0);
    }
  }

  @Test
  void uniqueConstraintOnDividendDetailTransactionIdExists() throws Exception {
    // El indice no-unico original (idx_dividend_detail_transaction_id) fue reemplazado por
    // V2026_06_14_07 con la constraint UNIQUE uq_dividend_detail_transaction_id.
    try (Connection conn = connection();
        ResultSet rs =
            conn.createStatement()
                .executeQuery(
                    "SELECT COUNT(*) FROM pg_indexes"
                        + " WHERE tablename = 'dividend_detail'"
                        + "   AND indexname = 'uq_dividend_detail_transaction_id'"
                        + "   AND indexdef LIKE '%UNIQUE%'")) {
      rs.next();
      assertThat(rs.getInt(1))
          .as("uq_dividend_detail_transaction_id unique index must exist")
          .isEqualTo(1);
    }

    try (Connection conn = connection();
        ResultSet rs =
            conn.createStatement()
                .executeQuery(
                    "SELECT COUNT(*) FROM pg_indexes"
                        + " WHERE tablename = 'dividend_detail'"
                        + "   AND indexname = 'idx_dividend_detail_transaction_id'")) {
      rs.next();
      assertThat(rs.getInt(1))
          .as("non-unique legacy index idx_dividend_detail_transaction_id must be dropped")
          .isZero();
    }
  }

  // -------------------------------------------------------------------------
  // TEST 4g — ON DELETE CASCADE behavioral verification
  // Uses direct JDBC; no Spring context needed.
  // -------------------------------------------------------------------------

  @Test
  void deleteTransactionCascadesDividendDetail() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID txId = UUID.randomUUID();
    UUID portfolioId = UUID.randomUUID();

    try (Connection conn = connection()) {
      conn.createStatement()
          .execute(
              "INSERT INTO users"
                  + " (id, username, email, password_hash, active, created_at, updated_at)"
                  + " VALUES ('"
                  + userId
                  + "', 'cascade_user_"
                  + userId
                  + "', 'cascade_"
                  + userId
                  + "@test.com',"
                  + " 'hash', true, NOW(), NOW())");

      conn.createStatement()
          .execute(
              "INSERT INTO transaction"
                  + " (transaction_id, user_id, portfolio_entry_id, asset_symbol, asset_type,"
                  + "  transaction_type, quantity, price_per_unit, total_value,"
                  + "  created_at, updated_at)"
                  + " VALUES ('"
                  + txId
                  + "', '"
                  + userId
                  + "', '"
                  + portfolioId
                  + "',"
                  + " 'AAPL', 'STOCK', 'DIVIDEND', 0, 0, 50.00, NOW(), NOW())");

      conn.createStatement()
          .execute(
              "INSERT INTO dividend_detail (id, transaction_id, dividend_type, created_at)"
                  + " VALUES (gen_random_uuid(), '"
                  + txId
                  + "', 'CASH', NOW())");

      // Sanity check: detail row exists before delete
      ResultSet rsBefore =
          conn.createStatement()
              .executeQuery(
                  "SELECT COUNT(*) FROM dividend_detail WHERE transaction_id = '" + txId + "'");
      rsBefore.next();
      assertThat(rsBefore.getInt(1)).as("dividend_detail must exist before delete").isEqualTo(1);

      // Delete parent → should cascade
      conn.createStatement()
          .execute("DELETE FROM transaction WHERE transaction_id = '" + txId + "'");

      ResultSet rsAfter =
          conn.createStatement()
              .executeQuery(
                  "SELECT COUNT(*) FROM dividend_detail WHERE transaction_id = '" + txId + "'");
      rsAfter.next();
      assertThat(rsAfter.getInt(1))
          .as("ON DELETE CASCADE must remove dividend_detail when transaction is deleted")
          .isZero();
    }
  }
}
