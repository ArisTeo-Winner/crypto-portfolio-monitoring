# SPEC: Reconciliación de dividendos al eliminar una transacción

**Estado:** Draft
**Fecha:** 2026-11-08
**Autor:** Aristeo (owner)
**Producto:** portfolio **TRACKER** (no bróker, no exchange)
**Stack:** Spring Boot · PostgreSQL · posiciones **derivadas** de transacciones
**Relacionado:** ADR-0024/0027 (accrual + replay), ADR-0029 (/me/dividends), ADR-0030 (efectivo), ADR-0031 (retención), ADR-0014 (corporate actions)

---

## 1. Problema

Al registrar una COMPRA de un stock que paga dividendos, el sistema **auto-acredita** dividendos como transacciones `DIVIDEND` (basadas en la tenencia neta del usuario en cada ex-date). Hoy, al **eliminar** esa compra, el borrado hace *hard-delete* de la transacción + reconcilia la proyección de holdings y el P&L realizado, pero **NO toca las transacciones `DIVIDEND`** derivadas. Resultado: dividendos **huérfanos** que acreditan acciones que, según el historial corregido, el usuario nunca tuvo → datos inconsistentes (y, con ADR-0030, efectivo que no debió entrar).

## 2. Principio rector

Los dividendos **auto-acreditados son DERIVADOS**, no hechos independientes: son una **proyección** de `(historial de tenencia del usuario × corporate_action del símbolo)`. El entitlement ya se calcula así (`DividendEntitlementCalculator`: cantidad neta firmada de BUY/SELL/TRANSFER con `operationDate < ex-date`, ajustada por splits).

> **Regla:** cualquier mutación del historial de tenencia de un símbolo (eliminar COMPRA/VENTA/TRANSFER, o un split) debe **RE-DERIVAR** los dividendos auto-acreditados de ese símbolo para ese usuario. "Eliminar una compra" **no** es "borrar dividendos"; es **recalcular entitlement** y dejar los dividendos que correspondan.

Esto evita tanto los huérfanos (no borrar nada) como el borrado ciego (borrar de más cuando hay otras compras).

## 3. Reglas de producto (respuestas a las preguntas)

### 3.1 ¿Al eliminar una COMPRA se eliminan los dividendos asociados?
**No directamente — se reconcilian por entitlement.** Al eliminar una transacción que afecta tenencia de un símbolo `S` para el usuario `U`, el sistema re-deriva los dividendos auto-acreditados de `(U, S)`:
- Se **eliminan** los auto-dividendos cuyo entitlement en su ex-date pasa a **≤ 0** (ya no hubo tenencia → el dividendo no debió existir).
- Se **recalculan** (monto) los auto-dividendos cuyo entitlement **bajó pero sigue > 0** (quedan otros lotes): nuevo bruto = `acciones_netas × DPS` (el `amountPerShare` ya se persiste en `dividend_detail`, ADR-0029), re-aplicando el perfil de retención (ADR-0031).
- Se **conservan** sin cambio los que no varían.
Implementación recomendada (reusa ADR-0027): **eliminar los auto-dividendos de `(U, S)` y re-ejecutar el accrual por entitlement para `(U, S)`** contra el historial post-borrado (idempotente → reconstruye exactamente los correctos).

### 3.2 ¿Qué pasa si ya hubo otros movimientos (ventas parciales, splits)?
Se respeta el entitlement real:
- **Varias compras / ventas parciales:** el entitlement en cada ex-date usa la **tenencia neta** post-borrado; si otros lotes cubren esa fecha, el dividendo se **mantiene o reduce**, no se borra.
- **Splits:** el entitlement ya ajusta cantidades por fecha (ADR-0011, no-mutante). La re-derivación usa la cantidad split-ajustada a la ex-date.
- La re-derivación usa **la misma** `DividendEntitlementCalculator` que el alta, para que alta y reconciliación nunca difieran.

### 3.3 ¿Soft-delete vs hard-delete?
- **Transacción de COMPRA:** se mantiene **hard-delete** (intención explícita del usuario; es un tracker, su dato). No se introduce soft-delete en v1.
- **Dividendos derivados:** **hard-delete reconciliado** (se eliminan/ajustan), **pero con rastro de auditoría** (`AuditLogService`) de qué se eliminó/ajustó y por qué (`reason=delete-reconcile`), y —con ADR-0030— un **movimiento de efectivo compensatorio** que revierte el crédito. El corporate_action (referencia global) **nunca** se toca.
- Soft-delete / "deshacer" queda como **no-objetivo** de v1 (ver §6); si se quisiera "undo", el audit log + replay ya permiten reconstruir.

### 3.4 Casos borde
- **Dividendo "confirmado/pagado" (paymentDate ≤ hoy) vs solo programado:** el **estado de pago no decide** si el registro debe existir — lo decide el **entitlement**. Si post-borrado el entitlement a esa ex-date es 0, el auto-dividendo se elimina **aunque** su fecha de pago ya pasó; además se **revierte su crédito de efectivo** (ADR-0030) para no dejar caja fantasma. (No es un bróker: no hay clawback real con la cuenta; es consistencia interna del tracker.)
- **Dividendo capturado MANUALMENTE por el usuario:** **NUNCA** se toca por la reconciliación. Solo se reconcilian los **auto-acreditados**. ⚠️ Prerequisito: hoy **no existe un marcador** auto-vs-manual → se requiere un **origen** en el dividendo (p. ej. `origin = ACCRUED | MANUAL` en `dividend_detail`/transacción) para distinguirlos con seguridad.
- **Varias compras del mismo ticker:** al borrar una, las demás mantienen el entitlement; los dividendos se **recalculan**, no se borran (§3.2).
- **Borrar la última posición del instrumento:** el entitlement cae a 0 en todas las ex-date → se **eliminan todos** los auto-dividendos de `(U, S)`; el corporate_action global **permanece**; si el usuario re-registra, el accrual los vuelve a derivar (ADR-0024).

## 4. Diseño funcional

Extender `deleteTransactionById` (y por simetría, update que cambie cantidad/fecha) con un paso de reconciliación, **dentro de la misma transacción** del borrado:

```
deleteTransactionById(U, txId):
  1. borrar la transacción (hard)                      [existe]
  2. reconcileUserPortfolio(U)                          [existe]
  3. si la tx borrada afecta tenencia (BUY/SELL/TRANSFER) de símbolo S:
       reconcileAutoDividends(U, S):                    [NUEVO]
         a. borrar los DIVIDEND auto-acreditados (origin=ACCRUED) de (U,S)
            → cascada a dividend_detail; revertir crédito de efectivo (ADR-0030); audit
         b. re-ejecutar accrual por entitlement para (U,S) sobre el historial actual
            → recrea los dividendos correctos (idempotente)
  4. rebuildUserRealizedPnl(U) + snapshot              [existe]
```

- **Idempotencia / orden:** la reconciliación corre tras el borrado y la reconciliación de holdings, antes del snapshot, para que el snapshot refleje el estado consistente.
- **Solo del usuario:** jamás toca dividendos de otros usuarios ni la referencia global.
- **Símbolos no-dividend:** si `S` no tiene corporate_action DIVIDEND, el paso 3 es no-op barato.

## 5. User stories + criterios de aceptación (testeables)

**US-1 — Borrar la única compra elimina sus dividendos**
Como usuario, al eliminar la única compra de un ticker, no quiero ver dividendos de acciones que ya no tengo.
- **AC-1.1** Dado `U` con 1 COMPRA de `GOOGL` y N dividendos auto-acreditados, cuando `U` elimina esa compra, entonces los N dividendos auto de `GOOGL` quedan **eliminados** (0 filas `DIVIDEND` auto de GOOGL para `U`).
- **AC-1.2** El `corporate_action` DIVIDEND de GOOGL (global) **sigue existiendo** (no se borró).
- **AC-1.3** (con ADR-0030) El saldo de efectivo de `U` en la moneda del dividendo se **reduce** exactamente en la suma de los netos revertidos.
- **AC-1.4** Existe una entrada de auditoría por cada dividendo eliminado (`reason=delete-reconcile`).

**US-2 — Borrar una de varias compras recalcula, no borra**
- **AC-2.1** Dado `U` con 2 COMPRAS de `GOOGL` (10 + 5 acciones, ambas antes de la ex-date) y un dividendo auto por 15 acciones, cuando `U` elimina la compra de 5, entonces el dividendo **persiste** con bruto recalculado para **10** acciones.
- **AC-2.2** Si la compra eliminada era **posterior** a la ex-date (no daba derecho), el dividendo **no cambia**.

**US-3 — No tocar dividendos manuales**
- **AC-3.1** Dado un dividendo `origin=MANUAL` capturado por `U` para `GOOGL`, cuando `U` elimina una compra de GOOGL, entonces ese dividendo manual **permanece intacto**.

**US-4 — Ventas parciales y splits**
- **AC-4.1** Dado `U` con COMPRA 10 + split 2:1 + un dividendo auto por 20 acciones, cuando `U` elimina una **venta** posterior que no afectaba la ex-date, el entitlement se recalcula correctamente (20) y el dividendo no desaparece por error.
- **AC-4.2** La cantidad usada en la re-derivación es **split-ajustada a la ex-date** (misma lógica que el alta).

**US-5 — Dividendo ya pagado**
- **AC-5.1** Dado un dividendo auto con `paymentDate` pasada y entitlement post-borrado = 0, cuando `U` borra la compra, el dividendo se **elimina** y su crédito de efectivo se **revierte** (consistencia; no clawback externo).

**US-6 — Idempotencia / consistencia**
- **AC-6.1** Tras borrar, el estado de dividendos de `(U, S)` es **idéntico** al que se obtendría registrando el historial resultante desde cero (la reconciliación == re-derivación).
- **AC-6.2** El borrado + reconciliación es **atómico**: si algo falla, no quedan dividendos a medio reconciliar (misma transacción; rollback).

## 6. No-objetivos (v1)

- **No** eliminar ni modificar `corporate_action` (referencia **global** compartida entre usuarios).
- **No** tocar dividendos **capturados manualmente** por el usuario.
- **No** recalcular **impuestos de ejercicios fiscales cerrados** ni emitir/alterar CFDI (eso vive en el módulo tax, ADR-0017; si el año ya se declaró, el ajuste fiscal es manual/externo).
- **No** afectar a **otros usuarios** (el entitlement y la reconciliación son por-usuario).
- **No** introducir **soft-delete / papelera / undo** de transacciones en v1 (el audit log + replay bastan para reconstruir).
- **No** es un bróker: no hay **clawback real** de efectivo contra una cuenta externa; el efectivo es estado interno del tracker.

## 7. Prerequisitos / action items

1. [ ] **Marcador de origen** del dividendo: `origin = ACCRUED | MANUAL` en `dividend_detail` (o la transacción) + backfill (los existentes ≈ ACCRUED salvo los que el usuario reconozca como manuales). Sin esto no se puede reconciliar con seguridad.
2. [ ] `reconcileAutoDividends(userId, symbol)`: borra los `ACCRUED` de `(U,S)` (cascada detail + reversa de efectivo ADR-0030 + audit) y re-ejecuta el accrual por entitlement para `(U,S)`.
3. [ ] Cablear el paso 3 en `deleteTransactionById` (y en `updateTransaction` cuando cambie cantidad/fecha de un BUY/SELL/TRANSFER), en la misma transacción.
4. [ ] Tests: unit del entitlement post-borrado + IT end-to-end por cada US (borrar única, borrar una de varias, manual intacto, split/venta, pagado con reversa de efectivo, atomicidad).
5. [ ] (con ADR-0030) Movimiento de efectivo compensatorio al revertir un dividendo.
6. [ ] (con ADR-0031) La re-derivación re-aplica el **perfil de retención** correcto por holding.
