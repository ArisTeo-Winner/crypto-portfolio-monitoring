# ADR-0032: Modelo de moneda funcional + presentación (nativo/unificado) — costo al FX de compra, valor al FX de hoy

**Status:** Proposed
**Date:** 2026-11-13
**Deciders:** Aristeo (owner)
**Módulo:** `portfolio` (valuación `application.service` + capa de presentación), con FX desde `marketdata`
**Relacionado:** [[ADR-0009]] (costo en base USD al FX trade-date — se corrige aquí), [[ADR-0010]] (moneda de presentación / no sumar entre monedas), [[ADR-0017]] (FX **fiscal**, convención distinta — no confundir), [[project_mxn_costbasis_fx_distortion]] (bug que motiva el ADR), [[project_stockprice_cache_staleness]] (plumbing de la tasa "actual").

> Referencia de diseño: Snowball Analytics, traído por el owner como estándar objetivo (capturas 13.11.2026).

## Context

Hoy la valuación acumula el **costo base en una sola base USD** vía
`PortfolioService.toBaseCurrency(amount, currency, usdMxnRateOn(tradeDate))`
(ADR-0009), y la **presentación** re-convierte ese USD a la moneda de display con el FX
**de hoy** vía `MoneyPresentationConverter.toDisplay(value, ctx)` (ADR-0010).

Para un activo **comprado en MXN** y mostrado **en MXN**, eso hace un round-trip
**MXN → USD (FX trade-date) → MXN (FX hoy)**. El factor `(FX_hoy / FX_compra)` mete un
**ajuste cambiario artificial en el costo** (el valor sí cuadra, solo el costo se distorsiona).
Caso real MELI: ganancia REAL +2,236 MXN (+7.1%), pero el tracker muestra ~−600 MXN (pérdida).
Mismo síntoma en NVDA (comprada en MXN, GBM/SIC).

El owner quiere el comportamiento de Snowball, que separa **dos ejes de moneda independientes**
en la vista de posiciones y mantiene el **ledger de transacciones inmutable en moneda nativa**.
La app **ya hace bien el ledger** (`/me/transactions`: NVDA en MXN, GOOGL en USD, el selector de
base no las toca). Lo que falta es (a) **corregir el FX del costo** y (b) el **toggle
nativo↔unificado** en holdings.

## Decision

Introducir el concepto de **moneda funcional por posición/lote** = la moneda en que se
**ejecutó** la operación (ya persistida en `transaction.currency`). El costo se lleva y presenta
en su moneda funcional; **nunca** se round-trip-ea por una base intermedia para mostrarlo.

### 1. Regla de FX por naturaleza de la cantidad (núcleo de correctitud)
Cada monto se convierte con el FX que corresponde a **lo que representa**, no con un FX único:

| Cantidad | FX aplicado |
|---|---|
| **Costo base** (lo pagado) | FX de la **fecha de compra** (histórico, fijo) |
| **Valor actual** | FX de **hoy** |
| **P&L** en moneda X | `valor(FX_hoy) − costo(FX_compra)` |

Consecuencia esperada y **correcta**: el rendimiento difiere entre monedas (p. ej. NVDA
−7.52% USD vs −6.87% MXN) porque el FX **es parte del retorno** de un activo cross-currency.

### 2. Dos ejes de presentación independientes (solo Holdings)
- **Moneda base** (selector del usuario, p. ej. MXN/USD): define el **Total** y la conversión
  del modo unificado.
- **Modo por fila** (toggle "En la moneda de la posición" vs "En {base}"):
  - **Nativo:** cada fila en su moneda funcional (GOOGL en USD; BIMBO/MELI/NVDA en MXN).
  - **Unificado:** todas las filas convertidas a la base, aplicando la regla de FX §1.
  - El **Total NO cambia** entre nativo y unificado para una misma base (el toggle es
    presentación por-fila); el Total siempre se expresa en la base, convirtiendo por §1.

### 3. Transacciones = ledger inmutable nativo
La vista de operaciones **siempre** muestra montos en la moneda de ejecución y el selector de
base **no** las toca (un trade es un hecho histórico). **Ya es correcto; no se cambia.**

### 4. No sumar entre monedas (ADR-0010 intacto)
Los resúmenes por-moneda se **agrupan** (líneas separadas por divisa), nunca cross-sum. El
único Total "mezclado" es el del modo unificado, que es una conversión explícita a la base,
etiquetada como tal.

### 5. (Bonus) Valuar SIC en MXN directo
Para posiciones SIC compradas en MXN, valuar del **precio MXN de BMV** (databursatil) en vez de
`NASDAQ_USD × FX`, reduciendo capas de FX en el valor. Opcional, fase posterior.

### 6. Contrato con el frontend (alto nivel)
El backend expone, **por posición**: moneda funcional, costo nativo, valor nativo, y los montos
**ya convertidos a la base** (para el modo unificado) + el Total en la base. El frontend **no
re-deriva FX**: solo elige qué representación pintar según el toggle. Nombres exactos de campos
= Action Item (aditivo, no rompe el response actual).

## Options Considered

### Opción A — Moneda funcional + conversión en presentación por naturaleza (elegida)
Costo nativo, FX trade-date para costo y FX hoy para valor; backend entrega ambas
representaciones.
**Pros:** P&L cuadra con el estado de cuenta GBM en moneda nativa; habilita el toggle; general
a N monedas; el ledger no se toca.
**Cons:** dos convenciones de FX conviviendo (trade-date vs hoy) — se documenta; requiere FX
histórico confiable por fecha.

### Opción B — Parche: corto-circuito cuando `displayCurrency == tradeCurrency`
Mantener la base USD y, solo si display==moneda-de-compra, mostrar el costo pagado sin
round-trip.
**Pros:** cambio mínimo, arregla el caso 1:1.
**Cons:** no generaliza al modo unificado ni a ≥3 monedas; sigue acumulando en USD. **Rechazada**
como solución final (sirve como mitigación temporal si urge).

### Opción C — Status quo (todo en base USD, re-multiplicar en display)
**Cons:** es el bug. **Rechazada.**

## Trade-off Analysis

El riesgo real no es el código sino la **disciplina de FX**: separar nítido "FX de valuación
histórico (costo)", "FX de valuación actual (valor)" y el "FX **fiscal**" de [[ADR-0017]] (TC DOF
día hábil anterior) — son **tres convenciones** que no deben mezclarse. La Opción A paga ese
costo de claridad a cambio de P&L correcto y el toggle que pide el owner, sin tocar el ledger ni
los engines FIFO/ACB (que ya operan sobre lotes). Depende de FX histórico por fecha
(`fx_rate_daily`/Banxico) y de **consolidar la tasa "actual"** hoy fragmentada
(`fx_rate_daily` vs `stockPrices::USDMXN` Redis vs `market_fx_snapshot`).

## Consequences

**Más fácil:**
- P&L por activo cuadra con el broker en moneda nativa; holdings con toggle nativo/unificado.
- Base para el reporte fiscal (ADR-0017) al tener costo/valor por fecha y moneda bien tipados.

**Más difícil / a vigilar:**
- Tres convenciones de FX coexistiendo; documentar y nombrar distinto en código.
- Cobertura de FX histórico por cada fecha de compra; fallback cuando falte la tasa.
- Consolidar la fuente de la tasa "actual" (plumbing #3 de [[project_mxn_costbasis_fx_distortion]]).

**A revisitar:**
- Interacción con el FX fiscal de [[ADR-0017]] (misma fuente Banxico, convención distinta).
- Valuación directa en MXN de SIC (§5) vs NASDAQ×FX.

## Action Items

### Fase 1 — Corrección del FX de costo (núcleo)
1. [ ] Dejar de re-multiplicar el **costo** por el FX de hoy en presentación: el costo se
   expresa en su moneda funcional (nativo) o se convierte a la base con **FX trade-date**, nunca
   base-USD × FX-hoy. Ajustar `toBaseCurrency`/`applyPerformanceSnapshot`
   (`PortfolioService`) + la capa `MoneyPresentationConverter.toDisplay` /
   `PortfolioEntryPresentationService`.
2. [ ] Valor actual sigue al **FX de hoy**; P&L = valor(hoy) − costo(trade-date).
3. [ ] Tests: MELI (+2,236 MXN real), NVDA (MXN), un activo USD mostrado en MXN y viceversa;
   aserción de que el rendimiento difiere por moneda de forma correcta.

### Fase 2 — Toggle nativo/unificado en Holdings
4. [ ] Response de holdings aditivo: por posición → moneda funcional, costo/valor nativos +
   costo/valor convertidos a la base (regla §1) + Total en base. Contrato al frontend.
5. [ ] Totales agrupados por moneda (sin cross-sum) salvo el Total unificado explícito.

### Fase 3 — Consolidación FX y SIC (opcional)
6. [ ] Unificar la fuente de la tasa "actual" USD/MXN.
7. [ ] Valuar SIC comprado en MXN directo del precio BMV (databursatil).
