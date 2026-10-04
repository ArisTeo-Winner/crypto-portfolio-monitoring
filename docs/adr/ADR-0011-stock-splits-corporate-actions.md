# ADR-0011: Ajuste por Stock Splits (fuente estructurada Massive + Alpha Vantage), no-mutante

**Status:** Accepted
**Date:** 2026-10-03
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (ingesta/split), `portfolio` (ajuste en la proyección)
**Relacionado:** [[ADR-0009]] (trade-date FX, proyección no-mutante), [[ADR-0007]] (importadas read-only)

## Context

Al operar en EE.UU., los usuarios viven **stock splits** (y reverse/stock dividends). Los proveedores
de precio ya devuelven el **precio ajustado** por split, pero las **transacciones** guardan cantidad y
precio **pre-split** → la valuación multiplica cantidad pre-split × precio post-split y produce un P&L
falso. Caso real en cartera: **WETO** hizo un **reverse 100:1 el 2026-08-03**; una compra del 2026-07-31
(377.13 @ $0.11) quedó cruda → `current_value` 411.07 y P&L +890% falsos (lo correcto ≈ 3.77 títulos).

Escenario crítico: el usuario compró **pre-split** y **registra meses después** (post-split). El sistema
debe ajustar sin que el usuario sepa de splits ni haga cuentas.

## Decision

1. **Fuente del ratio: estructurada, no parseo de texto legal** (se descarta extraer el Item 5.03 del
   8-K / app extractora aparte). **Massive primario**, **Alpha Vantage secundario de verificación**.
   - Massive hace los dos patrones con el mismo endpoint: **`?ticker=WETO`** (histórico de un stock, para
     fetch-on-miss al alta) y **sin `ticker`** (lista global paginada por `next_url`, para el sweep).
   - AV `SPLITS&symbol=` da una 2ª fuente → **golden copy**: si coinciden → `CONFIRMED`; si no →
     `PENDING_REVIEW`. También fallback.
2. **Modelo canónico:** `shareMultiplier = split_to / split_from = 1 / historical_adjustment_factor`
   (AV: `split_factor` directo). Se tratan igual `forward_split`, `reverse_split`, `stock_dividend`.
3. **Dónde se consulta:** **nunca el API en el hot path.** API solo en **ingesta background**
   (fetch-on-miss al entrar un símbolo + sweep programado). La **tabla `stock_split`** es la fuente de
   verdad que lee la proyección; Redis opcional como caché de lectura (como la curva CETES). Los splits
   son **inmutables** → cache-forever.
4. **Detección pre/post:** por **comparación de fechas históricas** en la proyección:
   `transaction_date < split.execution_date` ⇒ la compra fue pre-split ⇒ ajustar. **La fecha de registro
   (`created_at`) NO interviene** → registrar meses después da el mismo resultado. Límite ex-date: `<`
   estricto (una operación el mismo día del `execution_date` ya es post-split).
5. **Ajuste no-mutante** (como [[ADR-0009]]): las transacciones quedan intactas; el ajuste se aplica al
   construir la proyección. Por cada transacción:
   `factor = Π shareMultiplier` de los splits del ticker con `execution_date > tx.date`;
   `qty*=factor`, `precioUnit/=factor`, `totalInvested` sin cambio. (WETO: 377.13 → 3.7713, 0.11 → 10.99.)
6. **Splits futuros:** la ingesta acepta `execution_date` futura (anunciados); el ajuste se activa solo
   cuando la fecha llega (la comparación por fecha lo maneja natural).
7. **Captura (retail no sabe de splits):** **import-first** (PDF GBM/DriveWealth = as-transacted por
   construcción). Manual: nudge "datos de tu comprobante, no tu saldo actual" + **guard de precio
   histórico** (compara lo capturado contra el close histórico ajustado/sin-ajustar de esa fecha para
   detectar la base; `PENDING_REVIEW` si no cuadra). (Guard = fase posterior.)

## Options Considered (resumen)
- **App extractora separada (Python) del Item 5.03:** rechazada — microservicio prematuro; el ratio ya
  viene estructurado de Massive/AV (ver análisis en la discusión de arquitectura).
- **Mutar cantidad en DB:** rechazada — rompe el registro autoritativo ([[ADR-0007]]) y no es reversible.
- **Elegida:** proveedor estructurado + ajuste no-mutante por fecha en la proyección.

## Consequences
- El P&L de posiciones con split deja de ser falso; WETO se corrige a ≈3.77 títulos.
- Nueva dependencia de ingesta (Massive/AV) y tabla `stock_split`; API fuera del hot path.
- La proyección gana un paso de ajuste por split (además del FX trade-date).
- **A revisar:** sweep programado con cursor; AV golden-copy/`PENDING_REVIEW`; guard de precio histórico;
  aplicar el ajuste también en `/history` (serie) además de las entries; generalizar a plataforma de
  eventos corporativos (insiders/13F/IPO) sobre el mismo pipeline.

## Action Items (Fase 1)
1. [ ] Tabla `stock_split` (+ `stock_split_sync`) — Flyway.
2. [ ] `StockSplitData` + `StockSplitProviderPort` (fetch) + `StockSplitPort` (lectura con fetch-on-miss).
3. [ ] `MassiveSplitAdapter` (Massive `?ticker=`), con `MassiveSplitCredentialsIT` (protocolo de APIs).
4. [ ] `CachedStockSplitAdapter` (fetch-on-miss + TTL + persistencia).
5. [ ] `PortfolioService`: aplicar el factor de split en `applySnapshot` (entries), por fecha.
6. [ ] Tests, con **WETO** como caso (377→3.77).

### Fases siguientes
- F2: sweep `@Scheduled` (cursor) + AV verificación/golden-copy + ajuste en `/history`.
- F3: guard de precio histórico + `PENDING_REVIEW`.
- F4: plataforma de eventos corporativos (insiders/13F/IPO) reusando el pipeline.
