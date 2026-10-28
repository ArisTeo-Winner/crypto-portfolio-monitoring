# ADR-0009: FX por fecha de operación (trade-date) para la valuación multi-moneda

**Status:** Accepted
**Date:** 2026-09-25
**Deciders:** Aristeo (owner backend)
**Módulo:** `portfolio` (valuación), `marketdata` (FX histórico)
**Relacionado:** [[ADR-0006]] (normalización FX v1, tasa actual), [[ADR-0008]] (Banxico primario)

## Context

[[ADR-0006]] normalizó el costo MXN→USD usando la **tasa actual** (v1 pragmático) y dejó
explícitamente como refinamiento v2 el **FX por fecha de operación**. El owner decide adoptar v2:
cada operación MXN se convierte con el **FIX de su propia fecha**, no con la tasa de hoy. Así el
retorno del activo queda separado del movimiento del peso (una compra a 18.90 y venta a 18.92 no
inventan P&L por revaluar todo a la tasa de hoy).

Banxico publica el histórico del FIX (serie **SF43718**) por rango:
`GET /SieAPIRest/service/v1/series/SF43718/datos/{fechaInicio}/{fechaFin}` (YYYY-MM-DD), header
`Bmx-Token`. El FIX de un día es inmutable una vez publicado.

## Decision

**La valuación convierte cada transacción MXN al FIX de su fecha de transacción.** Reemplaza la
tasa única actual de v1 en costo/valuación (holdings-performance, proyección de entries e
historia total).

- Puerto de consumo `FxRateHistoryPort.usdMxnRateOn(LocalDate)` → FIX de esa fecha o el vigente más
  reciente anterior (fin de semana/feriado).
- Puerto de proveedor histórico `UsdMxnFxRateHistoryProviderPort.fetchUsdMxnRates(from, to)`,
  implementado por `BanxicoRateAdapter` (SF43718 por rango).
- Caché **inmutable** `fx_rate_daily` (una fila por ticker+fecha). `CachedFxRateHistoryAdapter`:
  ante un día sin cobertura cercana trae el rango [fecha−10d, fecha] de Banxico y lo persiste; los
  días ya cacheados no se vuelven a pedir. Si no hay dato histórico ni tras el fetch, cae a la tasa
  spot actual (`FxRatePort`, poblada por [[ADR-0008]]) como último recurso.
- `PortfolioService` (holdings-performance + `applySnapshot` de entries) y
  `GetPortfolioTotalHistoryService` (`toAccountingTransaction`) resuelven la tasa **por transacción**
  con la fecha de la operación en vez de una tasa única.

## Options Considered

### Option A: FX de la fecha de operación con caché diaria inmutable (elegida)
**Pros:** contabilidad correcta (separa retorno de activo vs divisa); el FIX es oficial e inmutable
→ caché permanente barata; fallback a spot evita regresión. **Cons:** una tabla + resolver nuevos;
la serie de valor sigue en USD (qty × precio USD) — solo se convierte costo/flujos.

### Option B: Mantener tasa actual (v1, [[ADR-0006]])
**Cons:** mete el movimiento del peso dentro del P&L; el owner lo descartó al pedir v2. **Rechazada.**

## Consequences

- El costo, `totalInvested`, `absoluteGain` y el MWR de operaciones MXN usan el FX de su fecha.
- Nueva tabla `fx_rate_daily` (migración `V2026_09_25_01`). El spot actual (`market_fx_snapshot`,
  [[ADR-0008]]) sigue existiendo solo como fallback del resolver histórico.
- La **serie de valor** del gráfico no cambia: el motor de agregación valúa qty × precio de mercado
  (USD); solo costo/flujos se convierten por fecha.
- **A revisar (v2+):** el precio de mercado histórico de emisoras MXN aún enruta por `AssetType`
  (no por moneda) — el premium SIC vs Nasdaq sigue pendiente ([[ADR-0006]]).

## Action Items
1. [x] Tabla `fx_rate_daily` + entidad + repositorio.
2. [x] `FxRateHistoryPort` + `UsdMxnFxRateHistoryProviderPort`.
3. [x] `BanxicoRateAdapter.fetchUsdMxnRates` (SF43718 por rango).
4. [x] `CachedFxRateHistoryAdapter` (caché inmutable + fetch-on-miss + fallback a spot).
5. [x] `PortfolioService` y `GetPortfolioTotalHistoryService` convierten por fecha de transacción.
6. [x] Tests: adapter de rango, resolver (hit/miss/fallback), y los de valuación migrados a
   `FxRateHistoryPort`.
7. [ ] Ejecutar `mvn test -Dtest=*CredentialsIT` con `BANXICO_TOKEN` real (DoD del protocolo de APIs).
