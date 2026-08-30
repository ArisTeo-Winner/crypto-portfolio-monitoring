# ADR-0003: Modelado de fricción de corretaje (comisión/IVA/fees) para costo base

**Status:** Proposed
**Date:** 2026-08-18
**Deciders:** Aristeo (owner backend)
**Módulo:** `transaction` (dominio puro en `transaction.domain.friction`); consumido por `statementimport`

## Context

El P&L se distorsiona si el costo de adquisición no incluye la fricción del broker (comisión, IVA,
regulatory fees). Dos rutas confirmadas contra tickets reales del usuario:

- **MXN / GBM casa de bolsa** — ticket FMTY 14 (captura de app): Bruto `100 × 15.50 = 1,550.00`,
  comisión `0.25%`, IVA `16%` sobre la comisión. Total reportado **$1,554.49**.
- **USD / DriveWealth** — confirmación CRCL (PDF): `Principal 103.76 + Commission 0.25 +
  Transaction Fee 0.00 + Other Fees 0.00 = Net 104.01`. **Sin IVA.**

### Hallazgos que corrigen el spec original

1. **DriveWealth sí cobra comisión** ($0.25 en el ticket real). La suposición de "cero comisión,
   solo regulatory fees" es falsa. `fee = Net − Principal = Commission + Transaction Fee + Other
   Fees`.
2. **Regla de redondeo GBM:** la comisión cruda `3.875` se **trunca** a `3.87` para presentación,
   pero el IVA se calcula sobre la comisión **de precisión completa**: `3.875 × 0.16 = 0.62`.
   Recalcular el IVA sobre `3.87` daría `0.6192` y no cuadraría con el ticket.
3. **La auditoría `IVA == comisión × 0.16` es solo MXN/renta variable.** Las confirmaciones USD no
   tienen línea de IVA.
4. **El estado mensual GBM-MX no es una fuente de compras de activo:** sus filas son *reportos* y
   *Compra DOLARES* (mercado de dinero / FX). La columna `NETO` es efectivo de liquidación, no costo
   de adquisición. Requiere filtrado por tipo de fila antes de aplicar cualquier regla NETO.

### Restricción del código existente

`AcbEngine` (`portfolio.domain.engine`) y `TransactionRealizedPnlService` **ya** integran `fee` al
costo base (buy: `+fee`; sell: `−fee`). El único gap es que `fee` es un valor plano sin desglose
auditable ni estatus de revisión.

## Decision

Tratar `fee` como **única fuente de verdad** que alimenta los motores de costo base ya existentes, y
añadir el desglose comisión/IVA/otros **solo como datos de auditoría**, con el invariante
`brokerCommission + brokerIva + otherFees == totalFrictionCost == fee`. Los montos reportados se
**leen** del documento; solo se **recalcula para validar** con tolerancia ±0.05 →
`REQUIERE_REVISION`.

## Options Considered

### Option A: `fee` único + desglose de auditoría (elegida)

| Dimensión        | Assessment                                          |
| ---------------- | --------------------------------------------------- |
| Complejidad      | Baja                                                |
| Riesgo de drift  | Nulo — no duplica la matemática de costo base       |
| Team familiarity | Alta — mismo patrón que `AcbEngine`                 |

**Pros:** motores ACB/PnL intactos; desglose informativo; una sola fuente de verdad.
**Cons:** exige mantener el invariante de suma en el parser.

### Option B: "Friction engine" que persiste `net_cost`/`adjusted_unit_price`

| Dimensión       | Assessment                                       |
| --------------- | ------------------------------------------------ |
| Complejidad     | Media                                            |
| Riesgo de drift | Alto — dos cálculos de costo base que divergen   |

**Pros:** 1:1 con el JSON del spec.
**Cons:** `net_cost` almacenado se desincroniza si cambia `fee`; duplica ACB/PnL.

## Trade-off Analysis

`final_net_cost` y `adjusted_unit_price` son **derivados** (`gross ± fee`, `netCost / quantity`).
Persistirlos crea una segunda fuente de verdad frente al `openCostBasis` que ya calcula el ACB.
Option A los expone como campos **calculados** en el DTO de respuesta, sin persistirlos.

## Consequences

- **Más fácil:** auditar en la UI (desglose visible); los motores de PnL no cambian.
- **Más difícil:** el parser debe poblar `commission`/`iva` de forma consistente por broker.
- **A revisar:** la ruta MX-estado-mensual necesita filtrado por tipo de fila antes de tocarse.

## Action Items

1. [x] `GbmFrictionCalculator` (dominio puro) + tests golden FMTY (1,554.49) y CRCL (104.01).
   → `transaction.domain.friction`, 8 tests verdes.
2. [ ] Migración Flyway aditiva en `transaction`: `broker_commission`, `broker_iva`, `other_fees`,
   `review_status` (todas nullable).
3. [ ] Estrategia de parser por broker/moneda: MX-equity, DriveWealth-USA, MX-mensual (con filtro de
   fila).
4. [ ] DTO `record` de respuesta con los derivados para la UI.
5. [ ] Conseguir una confirmación GBM-MX con compra de acción (no solo la captura) para test de
   integración del parser MX.
