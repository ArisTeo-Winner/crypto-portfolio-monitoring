# ADR-0004: Fricción por unidad (`perUnitFriction`) como campo derivado en el desglose

**Status:** Accepted
**Date:** 2026-09-17
**Deciders:** Aristeo (owner backend)
**Módulo:** `transaction` (dominio `transaction.domain.friction`; DTO `application.dto.response`)
**Relacionado:** [[ADR-0003]] (derivados calculados, no persistidos)

## Context

El frontend necesita mostrar el "puente" entre el precio de ejecución y el precio de
equilibrio en el detalle de la transacción:

```
Precio de ejecución/u    $108.80
  + fricción por unidad   $0.26   ← este dato
Precio de equilibrio/u   $109.06
```

El contrato del repositorio de frontend (su propio ADR-0001) prohíbe que la UI haga
aritmética de dinero: `FrictionBreakdownCard` documenta "Nothing is recomputed
client-side". Calcular `$0.26 = adjustedUnitPrice − pricePerUnit` o
`totalFrictionCost / quantity` **en el cliente** violaría ese contrato. El backend debe
exponer el valor ya calculado.

### Restricción de consistencia con ADR-0003

ADR-0003 fijó el principio: `finalNetCost` y `adjustedUnitPrice` son **derivados**
(`gross ± fee`, `netCost / quantity`) y se exponen como **campos calculados en el DTO de
respuesta, sin persistirse**, para no crear una segunda fuente de verdad frente al
`openCostBasis` que ya calcula el `AcbEngine`. `perUnitFriction` es otro derivado del mismo
tipo y debe recibir el mismo tratamiento.

## Decision

Exponer `perUnitFriction = totalFrictionCost / quantity` (escala 8, `HALF_UP`) como campo
**calculado, no persistido** en `FrictionBreakdown` (dominio) y `FrictionBreakdownView`
(DTO). No se agrega columna a la tabla `transaction` ni migración Flyway.

Invariante garantizado por construcción (porque `grossAmount = quantity × pricePerUnit`):

```
perUnitFriction == adjustedUnitPrice − pricePerUnit
```

El frontend renderiza el puente en una línea leyendo el campo por nombre; con la píldora
guardada por `perUnitFriction != null` queda inerte hasta que el payload lo incluya.

## Options Considered

### Option A: Campo derivado calculado, no persistido (elegida)

| Dimensión        | Assessment                                             |
| ---------------- | ------------------------------------------------------ |
| Complejidad      | Baja — dos puntos de cálculo, cero esquema             |
| Riesgo de drift  | Nulo — no persiste; se recomputa por request           |
| Consistencia ADR | Alta — mismo patrón que `adjustedUnitPrice` (ADR-0003) |
| Contrato FE      | Cumple — la UI no hace aritmética                       |

**Pros:** cero migración; una sola fuente de verdad; respeta ADR-0001 (FE) y ADR-0003 (BE).
**Cons:** dos sitios de cálculo (calculador de dominio + `buildFrictionBreakdown`), ya
existentes para `adjustedUnitPrice`.

### Option B: Cálculo client-side en el frontend

**Pros:** cero cambio de backend.
**Cons:** viola el contrato "UI never does money arithmetic"; duplica la matemática de
dinero fuera del dominio. **Rechazada.**

### Option C: Persistir `per_unit_friction` en la tabla `transaction`

**Pros:** disponible sin recomputar.
**Cons:** viola ADR-0003 — segunda fuente de verdad que se desincroniza si cambia `fee` o
`quantity`; migración innecesaria para un derivado. **Rechazada.**

## Trade-off Analysis

El valor es puramente derivado de datos ya presentes (`totalFrictionCost`, `quantity`).
Persistirlo (Option C) reintroduce el anti-patrón que ADR-0003 rechazó. Calcularlo en el
cliente (Option B) rompe el contrato del frontend. Option A es la única que satisface ambos
contratos y mantiene el desglose como proyección de solo lectura sobre `fee`.

## Consequences

- **Más fácil:** el frontend pinta el puente ejecución→equilibrio sin aritmética propia.
- **Más difícil:** nada material; el cálculo reusa el guard de `adjustedUnitPrice`.
- **A revisar:** si algún día se persiste el principal del PDF para conciliación al centavo
  (ver `docs/frontend-handoff-broker-friction.md`), revisar que `grossAmount` siga siendo
  `quantity × pricePerUnit` para que el invariante `perUnitFriction == adjustedUnitPrice −
  pricePerUnit` se mantenga exacto.

## Action Items

1. [x] `FrictionBreakdown` (dominio) + `GbmFrictionCalculator.build()`: campo y cálculo
   `totalFriction / quantity` (escala 8, HALF_UP), con guard `quantity == 0 → 0`.
2. [x] `FrictionBreakdownView` (DTO) + `TransactionService.buildFrictionBreakdown()`:
   `fee / quantity` (escala 8, HALF_UP), guard `null` como `adjustedUnitPrice`.
3. [x] Test golden CRCL: `perUnitFriction == 0.26214052`.
4. [x] `OpenApiTransactionDocumentationIT`: contrato expone `perUnitFriction`.
5. [ ] Frontend: encender la píldora del puente al confirmar el campo en el payload.
