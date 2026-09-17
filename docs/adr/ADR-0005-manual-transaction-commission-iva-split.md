# ADR-0005: Desglose de comisión/IVA en el alta manual de transacciones

**Status:** Accepted
**Date:** 2026-09-17
**Deciders:** Aristeo (owner backend)
**Módulo:** `transaction` (DTOs `application.dto.request`, `application.service`, dominio `friction`)
**Relacionado:** [[ADR-0003]] (fee = fuente de verdad + desglose de auditoría), [[ADR-0004]] (perUnitFriction derivado)

## Context

El alta manual (`POST /api/v1/me/transactions/buy` y `/sell`) aceptaba **solo un `fee`
único**. Resultado: `brokerCommission`/`brokerIva`/`otherFees` quedaban `null` y el desglose
de fricción caía a la vista gruesa (`totalFrictionCost == fee`). Solo el import DriveWealth
poblaba el split.

El usuario necesita registrar a mano tickets GBM donde el comprobante **sí** trae el desglose
(p. ej. GBM-MX con IVA 16% sobre la comisión; o producto USA como WETO **sin** IVA). Sin
split, se pierde la auditoría comisión vs IVA que ADR-0003 introdujo para el import.

## Decision

Agregar campos **opcionales** `brokerCommission`, `brokerIva`, `otherFees` (BigDecimal,
`@PositiveOrZero`) a `BuyTransactionRequest`/`SellTransactionRequest`. Cuando **cualquiera**
viene presente:

1. El backend **deriva** `fee = comisión + IVA + otros` (fuente de verdad única; si el
   cliente manda `fee` además del split, el split gana). Nunca se persisten dos totales.
2. Se calcula el `FrictionBreakdown` con `GbmFrictionCalculator.manual(...)` (bruto =
   `quantity * unitPrice`, `reportedTotal = null → ReviewStatus.OK`) y se aplica con
   `Transaction.applyFriction(...)`, poblando el split igual que el import DriveWealth.
3. Sin ningún campo de split → comportamiento clásico intacto (solo `fee`, vista gruesa).

**IVA como monto absoluto, no como tasa.** El usuario lee la comisión y el IVA del
comprobante; el backend los recibe tal cual y **no** recalcula desde 16% (así cuadra al
centavo con el ticket y se soporta el caso USA sin IVA). El autocálculo 16% editable vive en
el frontend.

**Compatibilidad:** los records agregan los 3 campos al final del constructor canónico + un
**constructor secundario** con la firma vieja (15 args) que delega con `null`. Así
`GbmStatementImportService` y los tests que construyen posicionalmente no se rompen, y Jackson
sigue deserializando por el constructor canónico.

## Options Considered

### Option A: split opcional en el alta manual (elegida)

**Pros:** paridad de auditoría con el import; `fee` sigue siendo fuente de verdad (derivado
de la suma); cero migración (usa columnas `broker_commission`/`broker_iva`/`other_fees` que ya
existen desde ADR-0003); reutiliza el motor de fricción.
**Cons:** dos representaciones de entrada (fee simple vs split) — se mitiga con la regla "split
gana y deriva fee".

### Option B: seguir solo con `fee` único

**Pros:** cero cambio.
**Cons:** el alta manual nunca desglosa; para GBM-MX con IVA el usuario suma a mano y pierde
la auditoría. **Rechazada** por el requerimiento del usuario.

### Option C: recibir tasa de IVA y recalcular en backend

**Cons:** riesgo de no cuadrar al centavo con el comprobante (regla de redondeo GBM,
ADR-0003); no cubre USA sin IVA con una sola tasa. **Rechazada.**

## Consequences

- **Más fácil:** registrar a mano tickets GBM con desglose auditable (comisión vs IVA);
  el detalle muestra el desglose fino en altas manuales, no solo en imports.
- **Más difícil:** el frontend debe decidir cuándo manda split vs `fee`; documentado que el
  split gana.
- **A revisar:** no se valida que `comisión + IVA + otros ≤ bruto` (solo `≥ 0`); añadir si
  aparece abuso. `import_source` permanece `MANUAL` (null) aunque haya split —
  `brokerCommission != null` es lo que dispara la vista fina.

## Action Items

1. [x] `BuyTransactionRequest`/`SellTransactionRequest`: 3 campos opcionales + constructor de
   compatibilidad.
2. [x] `GbmFrictionCalculator.manual(side, qty, unitPrice, commission, iva, otherFees,
   reportedTotal)`.
3. [x] `TransactionService`: `resolveManualFee` (deriva fee), `manualBreakdown`,
   `applyManualFriction` en buy y sell.
4. [x] Tests golden: split con IVA (1554.49) y USA sin IVA (WETO 41.56).
5. [ ] Frontend: campos Comisión + IVA (autocálculo 16% editable, $0 para USA) que manden el
   split; no mandar `fee` cuando hay split.
