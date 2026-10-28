# ADR-0007: Transacciones importadas son de solo lectura en el update (solo notas)

**Status:** Accepted
**Date:** 2026-09-19
**Deciders:** Aristeo (owner backend)
**Módulo:** `transaction` (application.service, domain.exception, inbound.rest.problem)
**Relacionado:** [[ADR-0003]], [[ADR-0005]]

## Context

`PUT /api/v1/me/transactions/{id}` (updateTransaction) permitía editar **cualquier**
transacción, sin distinguir su procedencia (`import_source`). Pero las importadas
(DriveWealth, GBM equity/statement) son el **registro autoritativo del broker**, tomado del
comprobante/PDF: editar cantidad, precio, fecha o fee **rompe la conciliación** con el estado
de cuenta y corrompe la auditoría de fricción (comisión/IVA reportados). El import es
**automático, sin interacción humana**; el usuario no debería poder alterarlo a mano.

Las manuales, en cambio, las capturó el usuario: son suyas para corregir.

## Decision

En `updateTransaction`, si la transacción es **importada**, rechazar cualquier cambio a
campos financieros; solo `notes` es editable.

- **Importada** = `import_source ∈ {DRIVEWEALTH, GBM_STATEMENT, GBM_EQUITY}`. **No** basta
  `!= null`: las altas manuales se persisten con `import_source = MANUAL` (no null), así que
  el guard usa `isImported(source) = source != null && source != MANUAL`.
- Si el request cambia símbolo, tipo, cantidad, precio, fecha o fee de una importada →
  `ImportedTransactionNotEditableException` → **Problem Detail RFC 9457, 409 Conflict**.
- Comparación tolerante: `BigDecimal.compareTo` (no `equals`) para montos; `Instant` para la
  fecha; un campo `null` en el request se trata como "sin cambio".
- Para importadas, solo se aplica `notes` (+ `updatedAt`) y se salta reconcile/rebuild de P&L
  (las notas no afectan cálculos).
- La defensa vive en el **backend**; el frontend deshabilita los campos por UX usando el
  campo `source` del detalle.

## Options Considered

### Option A: Guard por `import_source` en el backend + UX en frontend (elegida)
**Pros:** defensa real (ni un request directo corrompe un importado); el frontend es solo
comodidad; cero migración. **Cons:** comparación campo a campo (mitigada con helpers tolerantes).

### Option B: Solo deshabilitar en el frontend
**Pros:** cero backend. **Cons:** un request directo a la API sigue corrompiendo el importado;
la integridad no puede depender del cliente. **Rechazada.**

### Option C: Bloquear el update completo de importadas (ni notas)
**Pros:** más simple. **Cons:** anotar un importado es legítimo y no toca el registro
financiero. **Rechazada.**

## Consequences

- **Más fácil:** el registro del broker queda protegido; el frontend habilita/deshabilita por
  `source` sin lógica de negocio propia.
- **Más difícil:** el editor inline debe distinguir importado vs manual (ya tiene `source`).
- **A revisar:** si algún día se corrige un dato de un importado (error del broker), haría
  falta un flujo explícito (re-import o override auditado), no el update normal.

## Action Items
1. [x] `ImportedTransactionNotEditableException` + handler 409 en `TransactionExceptionHandler`.
2. [x] Guard `guardImportedFinancialFieldsUnchanged` + `isImported` en `updateTransaction`;
   rama importada aplica solo notas.
3. [x] Tests: rechazo de edición financiera + notas-only permitido sobre importada.
4. [ ] Frontend: editor inline deshabilita campos financieros cuando `source != "MANUAL"`.
