# ADR-0006: Moneda por operación y normalización FX en la valuación del portafolio

**Status:** Accepted (v1)
**Date:** 2026-09-23
**Deciders:** Aristeo (owner backend)
**Módulo:** `portfolio` (valuación), `marketdata` (puerto FX), `transaction` (snapshot)
**Relacionado:** [[ADR-0005]] (split comisión/IVA manual), [[ADR-0003]]

## Context

Operaciones de **Trading USA** (DriveWealth, USD) y **Trading MX** (GBM/BMV, MXN) conviven
en el mismo portafolio. Reproducido en vivo: una compra de MELI N en pesos (`pricePerUnit
31,562.38`, `currency=MXN`) se guardaba con el monto **crudo en MXN**, y la valuación lo
comparaba contra el precio de mercado de MELI en **USD** (Nasdaq) **sin tipo de cambio** →
P&L falso de **−72.77%** en el balance total.

Causa raíz doble: (a) el frontend no enviaba `currency`; (b) el backend no normalizaba FX en
la agregación del portafolio.

`currency` **ya existía** como campo en `BuyTransactionRequest` y como columna en
`transaction`, pero estaba infrautilizado: se guardaba (cuando llegaba) y no cascadeaba a la
valuación. `PortfolioTransactionSnapshot` (lo que consume el motor de agregación) **no**
llevaba `currency`. Existía infra FX (`HybridQuoteService.getUsdMxnRate()`, tabla
`market_fx_snapshot`, Banxico/DataBursatil) pero **no cableada** a la valuación.

## Decision

**`currency` es el marcador canónico** USA(USD) vs MX(MXN). Se captura en el alta (frontend) y
se persiste (ya existía). Para la valuación, **normalizar a una moneda base (USD)** aplicando
FX en el motor de agregación.

**v1 (este ADR):**
- `PortfolioTransactionSnapshot` gana `currency`; `TransactionHistoryAdapter` lo puebla desde
  `transaction.getCurrency()`.
- Puerto `FxRatePort` (en `marketdata.application.port.out`) con `usdMxnRate()`; adapter
  `CachedFxRateAdapter` sirve la tasa más reciente de `market_fx_snapshot` cuando está fresca
  (barato, sin llamada externa en el hot path). Portfolio ya consume puertos out de marketdata →
  arquitectura hexagonal respetada.

  **v1.1 (fix del cache vacío):** la tabla `market_fx_snapshot` solo se poblaba al golpear el
  endpoint `/fx/usdmxn` (nada la refresca en el path de valuación; el `@Scheduled` de
  `HybridQuoteService` refresca precios MXN, no el FX). En un entorno recién levantado la tabla
  estaba vacía → `usdMxnRate()` devolvía `empty` → la valuación **no convertía** y el P&L de MELI
  seguía crudo (−29,761). Solución: `CachedFxRateAdapter`, ante cache vacío o vencido (TTL
  `marketdata.fx.cache-ttl-minutes`, default 60), refresca en vivo vía
  `HybridQuoteService.getUsdMxnRate()` (que persiste el snapshot). Si el refresco falla, cae al
  cache vencido; si tampoco hay, devuelve `empty` (sin regresión). La dependencia
  `infrastructure → application.service` dentro del mismo módulo es válida (ArchUnit solo prohíbe
  `application → infrastructure`; sin ciclo porque `HybridQuoteService` no depende de `FxRatePort`).
- En `PortfolioService.applyPerformanceSnapshot`, `grossAmount`/`fee`/`unitPrice` de una
  operación `currency=MXN` se convierten a USD (÷ tasa) antes de acumular costo base y P&L. Sin
  tasa disponible → no convierte (comportamiento previo, sin regresión).

**Decisión de dinero (elegida por el owner): FX ACTUAL, no el de la fecha de operación.** El
costo MXN se revalúa a la tasa de hoy → el movimiento del peso queda dentro del P&L. Simple y
arregla el bug ya. El FX histórico por fecha (que separaría precio vs divisa) es refinamiento
v2.

## Options Considered

### Option A: Guardar nativo + normalizar FX en la valuación (elegida)
**Pros:** preserva el registro autoritativo del broker (fidelidad, como importados); permite
mostrar nativo y convertido; no depende del FX exacto de la fecha al escribir. **Cons:** la
agregación debe conocer la moneda (se agregó al snapshot).

### Option B: Convertir a USD al escribir (guardar USD)
**Cons:** pierde el monto nativo; hornea un FX (posiblemente incorrecto) en la escritura;
irreversible. **Rechazada.**

### Option C: Asumir una sola moneda (status quo)
**Cons:** el bug del −72%. **Rechazada.**

## Consequences

- **Más fácil:** el P&L del portafolio deja de mezclar MXN con USD; MELI N se valúa coherente.
- **Más difícil:** hay una dependencia FX en la valuación (puerto + tasa cacheada).
- **A revisar (v2):**
  1. **Enrutar price-history por moneda** (MXN→BMV/DataBursatil, USD→US providers): hoy el
     price-feed enruta por `AssetType`, no por moneda, así que MELI N registrado como `MELI`
     trae precio USD de Nasdaq (× FX ≈ correcto, ignora premium SIC).
  2. **FX histórico por fecha** para el costo (separar retorno de precio vs divisa).
  3. **`lastKnownPrice` en la serie**: el fallback sin quote vivo usa el precio convertido; con
     quote vivo (caso MELI) no aplica.

## Action Items
1. [x] `PortfolioTransactionSnapshot.currency` + `TransactionHistoryAdapter` lo puebla.
2. [x] `FxRatePort` + `CachedFxRateAdapter` (lee `market_fx_snapshot`, con refresco en vivo
   fetch-on-miss/stale + TTL — v1.1).
3. [x] `PortfolioService`: `toBaseCurrency` (MXN→USD, tasa actual) en `applyPerformanceSnapshot` y
   en la proyección de entries (`applySnapshot`, `/me/portfolio`).
4. [x] Test: costo MXN normalizado a USD en `getHoldingsPerformanceByPortfolioId` y en la
   proyección de entries.
5. [x] `GetPortfolioTotalHistoryService` (`/me/portfolio/history`): convierte `grossValue`,
   `price` y `fee` de cada transacción MXN→USD en `toAccountingTransaction`, de modo que
   `totalInvested`/`absoluteGain`/MWR queden en base USD frente a la serie (qty × precio USD,
   ya correcta). La serie de valor no requiere conversión: el motor de agregación solo usa la
   cantidad × el precio de mercado (USD). Test: `normalizesMxnBuyCostToUsdBaseInReturnMetrics`.
6. [x] Validado end-to-end en vivo: MELI N (compra MXN) pasa de `totalInvested 31,562.38 /
   P&L −29,761.16` a `1,802.05 / −0.83` tras poblar `market_fx_snapshot` (tasa 17.5147).
7. [ ] Frontend: enviar `currency` (ya implementado por QA) — coordinar prueba end-to-end.
8. [ ] v2: enrutamiento price-feed por moneda + FX histórico por fecha.
