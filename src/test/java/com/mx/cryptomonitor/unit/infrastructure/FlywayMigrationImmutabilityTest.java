package com.mx.cryptomonitor.unit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * Verifica que los archivos de migración Flyway ya aplicados no han sido modificados.
 *
 * <p>Flyway rechaza en arranque cualquier migración cuyo checksum difiera del almacenado en
 * flyway_schema_history. Este test detecta modificaciones antes de llegar a Docker, comparando el
 * CRC32 actual de cada archivo contra valores de referencia fijos.
 *
 * <p><b>Regla:</b> nunca modificar una entrada existente. Si se necesita corregir una migración ya
 * aplicada, crear una nueva migración de corrección.
 *
 * <p><b>Al agregar una nueva migración:</b> ejecutar {@code PrintMigrationChecksums} para obtener
 * el checksum y añadirlo al mapa EXPECTED_CHECKSUMS.
 */
class FlywayMigrationImmutabilityTest {

  /**
   * Checksums CRC32 de referencia por archivo de migración.
   *
   * <p>Valores calculados con {@link PrintMigrationChecksums} sobre el contenido normalizado a LF —
   * algoritmo idéntico al que usa Flyway internamente. Estos valores son inmutables una vez
   * registrados.
   */
  private static final Map<String, Long> EXPECTED_CHECKSUMS =
      Map.ofEntries(
          Map.entry("V1__baseline_schema.sql", 950141308L),
          Map.entry("V2__converge_existing_schema.sql", 3851596011L),
          Map.entry("V3__add_transaction_idempotency_keys.sql", 3020362484L),
          Map.entry("V2026_02_18_01__update_audit_log_events.sql", 1786701587L),
          Map.entry("V2026_04_25_01__backfill_user_timestamps.sql", 1702085796L),
          Map.entry("V2026_05_07_01__add_transaction_realized_pnl.sql", 1136446865L),
          Map.entry("V2026_05_21_01__increase_transaction_precision.sql", 1574739165L),
          Map.entry("V2026_05_26_01__increase_refresh_token_length.sql", 1348383442L),
          Map.entry("V2026_05_29_01__transaction_date_to_timestamptz.sql", 2571599070L),
          Map.entry("V2026_05_30_01__add_user_preferences.sql", 3972633310L),
          Map.entry("V2026_06_01_01__drop_refresh_tokens_table_and_column.sql", 191602315L),
          Map.entry("V2026_06_10_01__add_transaction_fields_and_dividend_detail.sql", 3346267960L),
          Map.entry("V2026_06_10_02__rename_asset_type_values.sql", 2997432758L),
          Map.entry("V2026_06_14_01__transaction_fields_and_dividend.sql", 3948000389L),
          Map.entry("V2026_06_14_02__rename_asset_type_values.sql", 3233817003L),
          Map.entry("V2026_06_14_03__asset_catalog.sql", 1942754340L),
          Map.entry("V2026_06_14_04__seed_asset_catalog.sql", 4139646851L),
          Map.entry("V2026_06_14_05__databursatil_market_data.sql", 2906847268L),
          Map.entry("V2026_06_14_06__migrate_legacy_futures_asset_type.sql", 3435136547L),
          Map.entry("V2026_06_14_07__dividend_detail_unique_transaction.sql", 2800148474L),
          Map.entry("V2026_06_14_08__populate_equity_logos.sql", 1436031483L),
          Map.entry("V2026_07_23_01__banxico_cetes_rates.sql", 1869022734L),
          Map.entry("V2026_07_24_01__reset_stock_logos_for_finnhub.sql", 1707031515L),
          Map.entry("V2026_07_26_01__populate_stock_and_crypto_logos.sql", 764997726L),
          Map.entry("V2026_08_12_01__create_statement_import_job.sql", 833575301L),
          Map.entry("V2026_08_12_02__add_statement_import_job_retry.sql", 1263541663L),
          Map.entry("V2026_08_18_01__add_transaction_friction_breakdown.sql", 1396543531L),
          Map.entry("V2026_08_24_01__add_transaction_import_source.sql", 3313832344L),
          Map.entry("V2026_09_13_01__asset_catalog_logo_status.sql", 3856886733L),
          Map.entry("V2026_09_20_01__asset_catalog_name_nullable_and_backfill.sql", 3793073697L));

  @Test
  void no_versioned_migration_file_must_be_modified_after_registration() throws IOException {
    List<String> violations = new ArrayList<>();

    URL dirUrl = getClass().getClassLoader().getResource("db/migration");
    assertThat(dirUrl).as("El directorio db/migration debe existir en el classpath").isNotNull();

    java.io.File dir = new java.io.File(dirUrl.getFile());
    java.io.File[] sqlFiles = dir.listFiles(f -> f.getName().matches("V.*\\.sql"));
    assertThat(sqlFiles).as("Debe haber archivos de migración").isNotNull().isNotEmpty();

    for (java.io.File file : sqlFiles) {
      String name = file.getName();
      long actual = computeChecksum(new java.io.FileInputStream(file));

      if (!EXPECTED_CHECKSUMS.containsKey(name)) {
        violations.add(
            String.format(
                "Nueva migración sin checksum registrado: '%s' (checksum=%dL).%n"
                    + "  → Añadir al mapa EXPECTED_CHECKSUMS en FlywayMigrationImmutabilityTest.",
                name, actual));
        continue;
      }

      long expected = EXPECTED_CHECKSUMS.get(name);
      if (actual != expected) {
        violations.add(
            String.format(
                "Migración modificada: '%s'%n"
                    + "  → esperado=%dL  actual=%dL%n"
                    + "  → Las migraciones aplicadas NO deben modificarse. Crea una nueva migración.",
                name, expected, actual));
      }
    }

    assertThat(violations)
        .as("Archivos de migración modificados o sin registrar detectados")
        .isEmpty();
  }

  @Test
  void all_registered_migration_files_must_exist_in_classpath() {
    List<String> missing = new ArrayList<>();

    for (String filename : EXPECTED_CHECKSUMS.keySet()) {
      URL resource = getClass().getClassLoader().getResource("db/migration/" + filename);
      if (resource == null) {
        missing.add(filename);
      }
    }

    assertThat(missing)
        .as(
            "Archivos registrados en EXPECTED_CHECKSUMS pero ausentes del classpath."
                + " Restaurar el archivo o eliminar la entrada del mapa.")
        .isEmpty();
  }

  // -------------------------------------------------------------------------

  /** CRC32 con saltos de línea normalizados a LF — mismo algoritmo que Flyway SQL migrations. */
  static long computeChecksum(InputStream is) {
    try (is) {
      CRC32 crc32 = new CRC32();
      byte[] content = is.readAllBytes();
      String normalized =
          new String(content, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
      crc32.update(normalized.getBytes(StandardCharsets.UTF_8));
      return crc32.getValue();
    } catch (IOException e) {
      throw new IllegalStateException("Error leyendo archivo de migración", e);
    }
  }
}
