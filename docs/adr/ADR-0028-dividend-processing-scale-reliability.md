# ADR-0028: Escala y confiabilidad del pipeline de dividendos (procesamiento tipo exchange)

**Status:** Accepted
**Date:** 2026-11-06
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (sync/replay), `transaction` (accrual), `shared` (executor), infra (cola durable futura)
**Relacionado:** [[ADR-0024]] (auto-poblado), [[ADR-0027]] (backfill late joiner), [[ADR-0014]] (accrual F3c), statement-import (patrón Postgres-as-queue + retry)

## Context

El modelo multi-usuario es **correcto** y está bien separado como un ledger de exchange: datos de referencia compartidos (`corporate_action` por símbolo), log de operaciones por usuario, y entitlement/settlement idempotente por `(usuario, símbolo, ex-date)`. El cálculo de entitlement maneja cualquier patrón (DCA, ventas graduales, bulk, holds, concurrentes).

Pero el pipeline fue construido **correcto-primero**, no endurecido para throughput/confiabilidad tipo exchange (cientos/miles de usuarios con operaciones concurrentes). Cuatro tensiones:

1. **Recompute desde todo el historial por evento:** `accrue()` carga TODAS las transacciones del símbolo y escanea a TODOS los usuarios, por CADA evento; ADR-0027 re-emite todos los dividendos por CADA registración → O(dividendos × historial × usuarios) en símbolos calientes.
2. **Entrega best-effort con descarte silencioso (riesgo de CORRECCIÓN):** el `@Async` del disparador de dividendos no nombra executor → usa `assetIconExecutor` (2-4 hilos, cola 500, **`DiscardPolicy`**). Bajo ráfaga, una tarea de accrual se **descarta en silencio** → un usuario podría perder su acreditación.
3. **Insert concurrente del catálogo:** dos syncs del mismo símbolo compiten por el unique natural; uno lanza (se loguea; el dato queda bien, pero no es limpio).
4. **Re-chequeo (F4-B) sin batching:** escanear todos los símbolos de golpe no escala.

La idempotencia del accrual (idempotency-key determinista + unique + manejo de `DataIntegrityViolation`) **ya protege contra duplicados** bajo concurrencia; el problema no es corrección de datos duplicados, sino **pérdida de tareas (#2)** y **costo/escala (#1, #3, #4)**.

## Decision

**Endurecer el pipeline de dividendos para escala tipo exchange, por FASES, priorizando la confiabilidad de entrega (#2) por ser riesgo de corrección.**

- **F1 — Executor dedicado sin descarte [ESTA ENTREGA]:** el disparador de dividendos corre en un `dividendProcessingExecutor` propio con cola acotada y **`CallerRunsPolicy`** (backpressure: bajo saturación la tarea corre en el hilo llamante, **nunca se descarta**). Separa el accrual del best-effort de íconos. Elimina el drop silencioso con cambio mínimo.
- **F2 — Cola durable + retry [HECHA]:** tabla `dividend_sync_job` (Postgres-as-queue, como `statement_import_job`). El listener de registro ya solo ENCOLA (dedup por símbolo activo); `DividendSyncJobWorker` (`@Scheduled`, `FOR UPDATE SKIP LOCKED` multi-instancia) drena: `sync` + `replayForAccrual`, con `DividendSyncJobLifecycleService` para claim/completed/failed (backoff exponencial 30s→15m, DEAD_LETTER a `max-attempts`). Sobrevive reinicios y reintenta. Gateado por `dividends.sync-worker.enabled` (off en tests).
- **F3 — Accrual scoped por usuario sobre proyección de posiciones:** acreditar solo al usuario afectado en la registración (no re-escanear a todos), y resolver la tenencia como lookup `shares-as-of-date` sobre la proyección de portfolio existente, no como fold del historial completo.
- **F4 — Hardening de concurrencia [HECHA]:** la fuente principal de syncs concurrentes del mismo símbolo en el diseño nuevo es la carrera del `exists`-check al encolar (dos registros → dos jobs → dos workers). Se cierra con un **índice único parcial** `uq_dividend_sync_job_active_symbol (asset_symbol) WHERE status IN ('QUEUED','PROCESSING')`: a lo sumo UN job activo por símbolo; el `enqueue` atrapa la violación y la trata como "ya encolado". El race del insert de `corporate_action` (rutas admin/re-chequeo) ya lo auto-sana el retry/reconcile idempotente de F2 (el perdedor reintenta y encuentra el existente), así que no requiere `ON CONFLICT`.
- **F5 — Batching del re-chequeo:** paginar el barrido de símbolos (cursor/keyset), con límites por corrida.

## Options Considered

### Option A: Dejar best-effort (status quo)
**Pros:** cero cambios. **Cons:** mantiene el **descarte silencioso de accrual** (#2) y los hotspots O(N×M). Inaceptable para el escenario tipo exchange. Descartada.

### Option B: Solo F1 (executor dedicado sin descarte)
| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Riesgo resuelto | **#2 (el de corrección)** |
| Esfuerzo | Mínimo |

**Pros:** elimina ya el riesgo de pérdida de acreditación con backpressure; aísla el accrual del executor de íconos. **Cons:** no resuelve durabilidad ante reinicio (#2 pleno) ni los costos de escala (#1). Es el **primer paso correcto**, no el final.

### Option C: Pipeline durable completo de una (cola Postgres + scoped accrual + proyección + concurrencia + batching)
| Dimensión | Evaluación |
|---|---|
| Complejidad | **Alta** |
| Esfuerzo | Grande (multi-módulo) |
| Riesgo de cambio | Alto si se hace de golpe |

**Pros:** la solución "tipo exchange" completa. **Cons:** gran superficie y riesgo de hacerlo en un solo salto; el proyecto ya demostró que el enfoque por fases con tests verdes funciona. Se adopta **como objetivo por fases**, no como big-bang.

## Trade-off Analysis

El eje es "¿big-bang o por fases, y qué primero?". El riesgo que un exchange nunca tolera es **perder una liquidación** (#2); es de corrección, no de rendimiento, así que va primero y con el cambio más pequeño que lo elimina (F1: backpressure en vez de descarte). La durabilidad ante reinicio (F2) y el costo O(N×M) (F1-análisis/F3) son importantes pero no pierden datos en operación normal, así que siguen. Hacer todo de una (Opción C) maximiza el valor final pero concentra riesgo; el proyecto ya validó que fase-a-fase con suite verde entrega sin regresiones. Se elige **B ahora, C como destino por fases**.

## Consequences

- **Más fácil:** el accrual deja de poder descartarse bajo carga (F1); el pipeline queda listo para crecer a cola durable (F2) sin re-arquitectura.
- **Más difícil / a vigilar:** `CallerRunsPolicy` da backpressure — bajo saturación extrema, la registración del usuario podría tardar un poco más (correcto: preferible a perder su dividendo). F2 lo elimina moviendo el trabajo a una cola durable.
- **A revisar:** medir el costo de O(dividendos × historial × usuarios) con datos reales antes de F3; si no duele a la escala objetivo, F3 puede esperar.

## Action Items

1. [x] (F1) `dividendProcessingExecutor` dedicado (cola acotada + `CallerRunsPolicy`); el disparador de dividendos lo usa. Sin descarte silencioso.
2. [x] (F2) Cola durable Postgres + retry/backoff para sync/replay/accrual (`dividend_sync_job` + worker + lifecycle, patrón statement-import).
3. [ ] (F3) Accrual scoped por usuario sobre proyección de posiciones (`shares-as-of-date`).
4. [x] (F4) Dedup atómico de la cola (índice único parcial + catch); el race del catálogo lo cubre el retry de F2.
5. [ ] (F5) Batching/keyset del barrido de re-chequeo (F4-B).
