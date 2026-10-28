# ADR-0013: Timestamp de operación (wall-time + zona IANA) vs timestamp de registro (UTC)

**Status:** Accepted
**Date:** 2026-10-12
**Decisiones del owner:** cada transacción lleva **dos** timestamps con semántica distinta — **Evento 1 (operación bursátil)** = wall-clock local + **nombre IANA** de la zona (`local_execution_time` + `execution_zone_id`), NO offset ni instante precomputado; **Evento 2 (registro en sistema)** = instante UTC (`created_at`, ya existe). Zona fijada **por fuente** de ingesta. Columna derivada e indexada `executed_at_utc` (TIMESTAMPTZ) como acelerador de orden (CQRS). Migración de legadas con **centinela `00:00:00`** + zona derivada por geografía del activo. Render frontend en **zona de la plaza, con etiqueta**.
**Deciders:** Aristeo (owner)
**Módulo:** `transaction` (entidad, DTO, motor FIFO/histórico), `statementimport` (parser DriveWealth/GBM), `marketdata` (reuso enum `Market` → zona), frontend (contrato de render)
**Relacionado:** [[ADR-0012]] (enum `Market` → `ZoneId` por mercado), [[ADR-0011]] (ajuste por splits), [[ADR-0009]] (valuación por fecha de operación), [[ADR-0007]] (transacciones importadas read-only)

## Context

La `Date` de las confirmaciones DriveWealth se mostraba **corrida un día** en el frontend: el PDF dice `Trade Date 6/24/2025`, el endpoint devuelve `"transactionDate":"2025-06-24T00:00:00Z"` (día correcto en UTC), pero el front lo renderiza en hora local (UTC−6) → `2025-06-23 18:00` → "Jun 23, 6:00 PM". No coincidía con ningún campo del comprobante (Execution Time, Trade Date ni Settle Date).

**Causa raíz y problema de modelo:**
1. El import guarda `tradeDate.atStartOfDay(ZoneOffset.UTC)` (`2025-06-24T00:00Z`): un **instante** derivado de una **fecha de calendario**, a medianoche UTC. Cualquier cliente al oeste de UTC baja el día al renderizar en local.
2. El parser **matchea pero descarta** la `Execution Time` (`11:27:29 AM`) del PDF; se pierde la hora-pared real de la bolsa.
3. Se **confunden dos hechos distintos** en un solo campo: *cuándo se ejecutó en el mercado* vs *cuándo mi backend registró el ticket*.

**Por qué NO guardar el instante precomputado (rechazo de la "Opción A" previa):** un instante UTC calculado hoy, o un offset fijo (`-04:00`), se **corre solo** si un gobierno cambia las reglas de DST o de zona para una fecha (crítico en eventos futuros/agendados) y pierde fidelidad a la hora que reportó la plaza. El **nombre IANA** de la zona es estable y permite recalcular el offset correcto en el momento del render.

## Decision

1. **Binomio inmutable de operación (Evento 1), fuente de verdad.** Dos columnas en `transaction`:
   - `local_execution_time` — `TIMESTAMP` sin zona (`LocalDateTime`): la hora-pared de ejecución en la plaza.
   - `execution_zone_id` — `VARCHAR` con **nombre IANA** (`America/New_York`, `America/Mexico_City`, `UTC`).

   Nunca `ZonedDateTime` en una sola columna (Hibernate lo normaliza a instante y no preserva el nombre). La resolución a instante se hace en el servicio: `ZonedDateTime.of(local, ZoneId.of(zoneName)).toInstant()`.

2. **Registro de sistema (Evento 2) = UTC.** Se mantiene `created_at` (`TIMESTAMPTZ`): el instante global en que el backend procesó el ticket (subida del PDF/captura). Correcto para un hecho de infraestructura ya ocurrido.

3. **Pipeline de ingesta — la zona se fija POR FUENTE:**
   | Fuente | `local_execution_time` | `execution_zone_id` |
   |---|---|---|
   | DriveWealth (PDF, USA) | `Trade Date` + `Execution Time` del PDF | `America/New_York` (forzado) |
   | GBM Statement (nacional) | hora del estado de cuenta | `America/Mexico_City` (forzado, aunque el activo sea US: manda la hora de la plaza que reporta) |
   | Crypto (Binance/Coinbase/Bitso, 24/7) | hora literal en escala **UTC** | `UTC` |
   | Alta manual | hora capturada | derivada del **mercado del ticker** |

   El `exchange="SIC"` que hoy hardcodea `DriveWealthImportService` es **ortogonal**: la zona viene de la fuente/mercado, no del código de exchange.

4. **Zona derivada en el backend (no en el cliente).** Para alta manual, el backend deriva `execution_zone_id` del **mercado del activo** reusando el enum `Market` de [[ADR-0012]] (map mercado→`ZoneId`). El frontend puede mostrar la zona, pero no es la fuente de verdad de un campo tan crítico; así las rutas import y manual comparten **un solo** mapeo y no se puede falsear desde el cliente.

5. **Orden cronológico = columna derivada `executed_at_utc` (TIMESTAMPTZ), patrón CQRS.** Se calcula al momento de la ingesta desde el binomio y se indexa:
   ```sql
   CREATE INDEX idx_transaction_chronology_utc ON public."transaction" (executed_at_utc DESC);
   ```
   El binomio local sigue siendo la **fuente de verdad inmutable**; `executed_at_utc` es un **acelerador** recalculable para el motor FIFO y el histórico (escala logarítmica, miles de registros en ms). Sin drift para hechos **pasados** (las reformas de DST alteran el futuro, casi nunca el pasado de forma retroactiva); si ocurriera, se recomputa desde el binomio.

6. **Parser DriveWealth: capturar la `Execution Time`.** Añadir grupo al `DATA_ROW_PATTERN` y campo a `ParsedConfirmationRow` (`LocalTime executionTime`). El import combina `tradeDate + executionTime` → `LocalDateTime`, zona `America/New_York`.

7. **Migración de filas legadas — centinela `00:00:00`.**
   - **Documentos (DriveWealth / GBM-statement):** se fuerza **re-importación** con el nuevo parser → binomio exacto.
   - **Manuales pasadas (hora desconocida):** el script inyecta **`00:00:00`** (valor centinela estándar en finanzas: "hora desconocida", no basura) combinado con la zona derivada por **geografía del activo**:
     - MELI → `00:00:00` `America/Mexico_City`
     - WETO → `00:00:00` `America/New_York`
     - Crypto → `00:00:00` `UTC`
   - Garantiza orden FIFO determinista del mismo día y es **reversible** si el usuario consigue sus comprobantes reales.

8. **Contrato de render (frontend) = zona de la plaza, con etiqueta.** Mostrar "la realidad del mercado" para contrastar sin confusión contra TradingView/CoinMarketCap/Finviz. El cálculo del offset lo hace el cliente resolviendo el **nombre de zona** (no hardcodear horas):
   - CRCL: `24 jun 2025, 11:27 a.m. (NY)` — tooltip opcional con hora del usuario (en verano NY va **2 h** adelante de México → `9:27 a.m. México`).
   - WETO (legada): `31 jul 2026, 12:00 a.m. (NY)` (centinela medianoche).
   - Crypto en vivo: `12 oct 2026, 03:54 a.m. (UTC)`.

   El DTO expone `localExecutionTime`, `executionZoneId` y `executedAtUtc` (instante ISO-8601 para quien prefiera convertir). Se mantiene `transactionDate` como alias de `executedAtUtc` durante la transición para no romper consumidores actuales.

## Consequences

**Positivas**
- La fecha deja de correrse: el día y la hora reflejan la ejecución real de la plaza en cualquier cliente/zona.
- Robusto a reformas de DST/zona (nombre IANA estable); separa limpio "mercado" de "infraestructura".
- Orden FIFO/histórico rápido y determinista vía índice; fuente de verdad protegida.
- Mapeo mercado→zona único (enum `Market`), compartido por import y alta manual.

**Negativas / costos**
- Migración Flyway con columnas nuevas + backfill; re-importar DriveWealth/GBM para exactitud.
- Cambio cross-cutting: entidad `Transaction`, `ParsedConfirmationRow`, DTO `TransactionResponse`, servicios de orden/valuación y contrato con frontend.
- Las filas legadas manuales quedan con centinela `00:00` (hora desconocida) hasta que haya comprobante.
- Comparaciones cronológicas deben usar `executed_at_utc` (o resolver el binomio), nunca comparar `local_execution_time` entre zonas distintas.

## Plan de implementación (por fases)

- **F1 — Esquema + dominio:** migración Flyway (`local_execution_time`, `execution_zone_id`, `executed_at_utc` + índice); entidad `Transaction`; helper de resolución a instante.
- **F2 — Ingesta:** parser DriveWealth captura `Execution Time`; `DriveWealthImportService` arma el binomio (NY); zona por fuente en los demás importers; alta manual deriva zona del `Market` del activo; cálculo de `executed_at_utc` al persistir.
- **F3 — Migración de datos:** re-import DriveWealth/GBM; script centinela `00:00` + zona por geografía para manuales legadas.
- **F4 — Contrato + frontend:** DTO expone el binomio + `executedAtUtc`; coordinar con la sesión de frontend el render polimórfico por perfil (zona de la plaza con etiqueta + tooltip).

## Appendix — Esquema objetivo

```sql
ALTER TABLE public."transaction"
  ADD COLUMN local_execution_time TIMESTAMP,         -- wall-clock de la plaza (sin zona)
  ADD COLUMN execution_zone_id    VARCHAR(64),        -- nombre IANA: America/New_York | America/Mexico_City | UTC
  ADD COLUMN executed_at_utc      TIMESTAMPTZ;        -- derivado (CQRS), recalculable desde el binomio

CREATE INDEX idx_transaction_chronology_utc ON public."transaction" (executed_at_utc DESC);
```

Resolución canónica (servicio):
```java
Instant executedAtUtc =
    ZonedDateTime.of(localExecutionTime, ZoneId.of(executionZoneId)).toInstant();
```
