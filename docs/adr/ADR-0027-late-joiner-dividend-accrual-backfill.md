# ADR-0027: Backfill de accrual de dividendos para el "late joiner"

**Status:** Accepted
**Date:** 2026-11-06
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (re-emite el evento de dividendo existente), `transaction` (acredita vía el listener existente)
**Relacionado:** [[ADR-0024]] (auto-poblado al registrar), [[ADR-0014]] (corporate actions framework / accrual F3c), [[ADR-0020]] (idempotencia / freeze-al-pago)

## Context

El modelo multi-usuario es correcto y separa dos planos: la **serie de dividendos** de un símbolo (compartida, en `corporate_action` + `dividend_action_detail`, por símbolo) y el **accrual por usuario** (una transacción `DIVIDEND` por cada usuario con derecho, calculado por `DividendEntitlementCalculator` sobre la tenencia neta de cada usuario con fecha de operación `<` ex-date). El cálculo de entitlement maneja DCA, holds, ventas, corto y largo plazo.

**El hueco:** el accrual se dispara **solo cuando se inserta un dividendo NUEVO** en el catálogo — `DividendSyncService` publica `DividendDetectedEvent` únicamente en su rama `created++`. Consecuencia:

- El **primer** usuario que registra el símbolo dispara el sync → se puebla el catálogo → se acreditan **todos los holders de ese momento**. ✅
- Dividendos **futuros** (que descubra el re-chequeo o un nuevo sync): nuevo insert → evento → se acreditan todos. ✅
- Un usuario que **se une DESPUÉS** de que el catálogo ya tiene los dividendos (ej. compra GOOGL cuando otro usuario ya pobló la serie): su sync encuentra todo existente → `created = 0` → **no se publica evento → su entitlement histórico NUNCA se acredita.** ❌

Es un bug de **corrección**: el late joiner ve la serie (catálogo compartido) pero pierde las transacciones `DIVIDEND` a las que tiene derecho por los pagos ocurridos mientras ya tenía las acciones.

## Decision

**Al registrar un holding (disparo de ADR-0024), después del sync, RE-EMITIR `DividendDetectedEvent` por cada dividendo YA existente del símbolo.** El listener de accrual existente (`DividendAccrualEventListener`) los procesa; la idempotencia (`exists(user, símbolo, ex-date)`) acredita **solo al late joiner** y salta a los que ya estaban. El propio javadoc de `DividendDetectedEvent` ya contempla la re-emisión como segura.

- Nuevo caso de uso en `marketdata`: `ReplayDividendAccrualUseCase.replayForAccrual(symbol)` — lee los `corporate_action` DIVIDEND del símbolo + su detalle y publica un `DividendDetectedEvent` por cada uno. Lectura pura; no toca el modelo ni la ingesta.
- `DividendSyncOnTransactionListener` (marketdata) hace, tras una registración: `sync(symbol)` **y luego** `replayForAccrual(symbol)`. Ambos `@Async` + `AFTER_COMMIT` + fallo contenido.
- Orden correcto por construcción: la transacción del nuevo holder ya está COMMITEADA (el evento de ADR-0024 es AFTER_COMMIT) antes de que el accrual cargue el historial, así que el nuevo usuario ya aparece en la tenencia.

## Options Considered

### Option A: Re-emitir los `DividendDetectedEvent` existentes tras el sync (ELEGIDA)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Reutilización | **Máxima** (mismo evento, mismo listener, misma idempotencia) |
| Acoplamiento | Nulo nuevo (no pasa userId; `accrue` ya escanea el historial) |
| Costo | Redundancia acotada (re-emite por registración; idempotente) |

**Pros:** reutiliza exactamente la ruta de accrual ya probada; la idempotencia garantiza que nadie se duplica y que solo el late joiner se acredita; sin userId ni plumbing nuevo; la re-emisión ya está declarada segura.
**Cons:** re-emite todos los dividendos del símbolo en cada registración (trabajo redundante, idempotente); en la **primera** registración se publica dos veces lo recién insertado (sync lo publica como nuevo + replay lo re-emite) — inocuo por idempotencia.

### Option B: Accrual "pull" scoped al usuario (puerto de consulta + userId)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media-alta |
| Reutilización | Parcial (nuevo puerto de consulta + `accrue` por-usuario) |

**Pros:** acredita exactamente al nuevo usuario, sin re-escanear a los demás.
**Cons:** requiere un puerto `RecordedDividendQueryPort` en marketdata, propagar el `userId` por el evento de ADR-0024, y un `accrue` scoped — más superficie y más acoplamiento para una ganancia de rendimiento irrelevante a esta escala. Queda como optimización futura si hiciera falta.

### Option C: No hacer nada / confiar en el re-chequeo

**Pros:** cero cambios. **Cons:** el re-chequeo (F4-B) solo re-acredita cuando descubre dividendos **nuevos**; los **pasados** del late joiner seguirían perdidos. No corrige el bug. Descartada.

## Trade-off Analysis

El eje es "¿re-usar la ruta de accrual existente (push idempotente) o construir una ruta pull scoped por usuario?". La ruta existente ya es **idempotente por diseño y su contrato ya promete seguridad ante re-emisión**, así que re-emitir es la solución de menor superficie, menor riesgo y máxima reutilización — exactamente lo que un cambio de corrección debe ser. La redundancia (re-escanear usuarios ya acreditados) es trabajo barato y seguro a esta escala; la alternativa scoped (B) añade un puerto, propaga identidad del usuario y duplica el `accrue` para ahorrar un cómputo que no es cuello de botella. Se elige A y se deja B anotada como optimización si el volumen lo pidiera.

## Consequences

- **Más fácil:** cualquier usuario que registre un holding con dividendos ya en catálogo recibe **retroactivamente** sus transacciones `DIVIDEND` correctas, sin declarar nada.
- **Más fácil:** cierra el flujo multi-usuario de punta a punta (serie compartida + accrual per-usuario completo para todos, incluidos los late joiners).
- **Más difícil / a vigilar:** re-emisión redundante por registración (acotada, idempotente); si el volumen de dividendos/holders por símbolo creciera mucho, migrar a la Opción B (accrual scoped por usuario).
- **A revisar:** el `assetType` del accrual sigue asumido como `STOCK` y `exchange` `null` (zona fallback NY), igual que la ruta push existente; si se acreditan ETFs/BMV con zona distinta, afinar ahí.

## Action Items

1. [ ] `ReplayDividendAccrualUseCase` (port-in marketdata) + impl en `DividendSyncService`: publica `DividendDetectedEvent` por cada `corporate_action` DIVIDEND del símbolo (con su detalle).
2. [ ] `DividendSyncOnTransactionListener`: tras `sync(symbol)`, llamar `replayForAccrual(symbol)` (async, fallo contenido).
3. [ ] Tests: replay emite un evento por dividendo existente; el late joiner se acredita y los demás se saltan (idempotencia); fallo contenido.
4. [ ] (Follow-up, si escala) Opción B: accrual scoped por usuario (puerto de consulta + userId).
