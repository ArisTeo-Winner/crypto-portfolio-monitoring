package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifica que las migraciones de Flyway crean las 3 tablas de cache de DataBursatil/BMV con las
 * columnas y restricciones esperadas.
 */
@Testcontainers(disabledWithoutDocker = true)
class MarketDataSchemaIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("crypto_portfolio_marketdata_test")
          .withUsername("test")
          .withPassword("test");

  @Test
  void migrationsCreateTheThreeBmvMarketDataTablesWithExpectedColumns() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .load();

    MigrateResult result = flyway.migrate();
    assertThat(result.success).isTrue();

    try (Connection connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

      // C1a: bmv_price_history con UNIQUE(emisora_serie, trade_date)
      assertThat(tableExists(connection, "bmv_price_history")).isTrue();
      assertThat(
              uniqueConstraintExists(
                  connection, "bmv_price_history", "emisora_serie", "trade_date"))
          .isTrue();

      // C1b: market_price_snapshot con columnas price_close, importe_operado, etc.
      assertThat(tableExists(connection, "market_price_snapshot")).isTrue();
      assertThat(columnExists(connection, "market_price_snapshot", "asset_symbol")).isTrue();
      assertThat(columnExists(connection, "market_price_snapshot", "price_close")).isTrue();
      assertThat(columnExists(connection, "market_price_snapshot", "importe_operado")).isTrue();
      assertThat(columnExists(connection, "market_price_snapshot", "quote_timestamp")).isTrue();

      // C1c: market_fx_snapshot con ticker, rate
      assertThat(tableExists(connection, "market_fx_snapshot")).isTrue();
      assertThat(columnExists(connection, "market_fx_snapshot", "ticker")).isTrue();
      assertThat(columnExists(connection, "market_fx_snapshot", "rate")).isTrue();
    }
  }

  private boolean tableExists(Connection connection, String tableName) throws SQLException {
    String sql =
        "select exists (select 1 from information_schema.tables "
            + "where table_schema = 'public' and table_name = '%s')".formatted(tableName);
    return queryBoolean(connection, sql);
  }

  private boolean columnExists(Connection connection, String tableName, String columnName)
      throws SQLException {
    String sql =
        "select exists (select 1 from information_schema.columns "
            + "where table_schema = 'public' and table_name = '%s' and column_name = '%s')"
                .formatted(tableName, columnName);
    return queryBoolean(connection, sql);
  }

  private boolean uniqueConstraintExists(
      Connection connection, String tableName, String... columnNames) throws SQLException {
    String columnsList =
        String.join(",", Arrays.stream(columnNames).map(c -> "'" + c + "'").toList());
    String sql =
        """
        select exists (
          select 1
          from information_schema.table_constraints tc
          join information_schema.key_column_usage kcu
            on tc.constraint_name = kcu.constraint_name
          where tc.table_schema = 'public'
            and tc.table_name = '%s'
            and tc.constraint_type = 'UNIQUE'
            and kcu.column_name in (%s)
          group by tc.constraint_name
          having count(distinct kcu.column_name) = %d
        )
        """
            .formatted(tableName, columnsList, columnNames.length);
    return queryBoolean(connection, sql);
  }

  private boolean queryBoolean(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getBoolean(1);
    }
  }
}
