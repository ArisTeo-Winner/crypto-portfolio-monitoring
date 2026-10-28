# ADR-0015: Ingesta administrada de corporate actions (endpoint admin para merger, extensible a tender/bankruptcy/spinoff)

**Status:** Accepted
**Date:** 2026-10-21 (implementado 2026-10-22: merger)
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (persistencia del evento + publicación), `transaction` (accrual vía listener)
**Relacionado:** [[ADR-0014]] (corporate actions framework — base CTI + efectos por usuario), [[ADR-0013]] (fecha de operación wall-time+zona), eventos cross-module Spring core (`@TransactionalEventListener`, ver dividendos F3c)

## Context

Tras ADR-0014 F6, los **7 tipos** de corporate actions tienen detalle bajo `corporate_action` y **efecto por usuario** implementado (merger→carryover, tender→cash-out, bankruptcy→write-off, spinoff→reparto de base, dividendos→accrual fiscal). Pero solo **dividendos** entran de punta a punta: hay un **productor** (feed Massive → `DividendSyncService` → evento → listener). Para **merger / tender / bankruptcy / spinoff NO existe feed**: los `*AccrualUseCase` son código alcanzable **sin invocador**. En la práctica, hoy una fusión no puede entrar al sistema.

Son eventos **esporádicos y de baja frecuencia** (una fusión o quiebra puntual en la cartera), capturados manualmente por el owner/operador. Se necesita una "puerta de entrada" que **(a)** registre el evento en `corporate_action` + su detalle (timeline/auditoría) y **(b)** dispare el efecto sobre las carteras.

**Fuerza de arquitectura clave (el nudo):** `transaction` ya depende de `marketdata` (vía `MarketZoneQueryPort`, `DividendDetectedEvent` — named interface `port-out`). Por tanto **`marketdata → transaction` crearía un ciclo** prohibido por `ModulithVerificationTest`. Además `corporate_action` es propiedad de `marketdata` (sus repos viven ahí); `transaction` no puede escribir esas tablas (ArchUnit lo prohíbe). La solución tiene que persistir el evento en `marketdata` y disparar el accrual en `transaction` **sin** que `marketdata` llame a `transaction`.

## Decision

**Endpoint admin en `marketdata` que persiste el evento y publica un evento de dominio; `transaction` lo acredita vía listener** — el mismo patrón que dividendos (F3c), que ya evita el ciclo.

1. **`POST /api/v1/admin/corporate-actions/merger`** (`ROLE_ADMIN`) en `marketdata.infrastructure.inbound.rest`. Body = términos de la fusión (predecesor, sucesor, tipo, ratio, cash/acción, moneda, fecha efectiva).
2. Handler → `RegisterMergerUseCase` (marketdata): persiste `corporate_action(MERGER)` + `merger_action_detail` **idempotente** por llave natural (símbolo+tipo+pivotal_date), y **publica `MergerDetectedEvent`** (en `marketdata.application.port.out`, named interface ya expuesta).
3. `MergerAccrualEventListener` (transaction inbound, `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`) consume el evento y llama al `MergerAccrualUseCase` existente (F6b-2). La idempotencia del accrual (keys deterministas) protege re-procesos.
4. `SecurityConfig`: la ruta `/api/v1/admin/corporate-actions/**` requiere `hasRole("ADMIN")`.

Patrón reutilizable: tender/bankruptcy/spinoff añaden su endpoint + `Register*UseCase` + `*DetectedEvent` + listener análogos, cada uno reusando su `*AccrualUseCase`.

## Options Considered

### Option A: Controller admin que llama directo al `*AccrualUseCase` (síncrono, cross-module)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja (una clase) |
| Acoplamiento | **Rompe Modulith** |
| Escalabilidad | N/A |
| Familiaridad | Alta |

**Pros:** lo más simple de leer; persistir + acreditar en una transacción.
**Cons:** si el controller vive en `marketdata`, llama a `transaction` → **ciclo** `marketdata↔transaction` (prohibido). Si vive en `transaction`, no puede escribir `corporate_action` (de `marketdata`) sin un puerto, y aun así mezcla la persistencia del evento con el accrual en una sola transacción (un fallo del accrual revertiría el registro del evento).

### Option B: Endpoint admin en `marketdata` → persiste + publica evento; `transaction` acredita vía listener (ELEGIDA)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media (endpoint + use case + evento + listener) |
| Acoplamiento | **Limpio** (`transaction → marketdata`, sin ciclo) |
| Escalabilidad | Buena (desacoplado; async opcional a futuro) |
| Familiaridad | Alta (idéntico a dividendos F3c) |

**Pros:** sin ciclo; el evento queda persistido aunque el accrual falle (listener AFTER_COMMIT en tx propia); reusa el patrón y los `*AccrualUseCase` ya probados; abre la puerta a un feed real sin cambiar el consumidor.
**Cons:** más piezas; el accrual corre tras el commit (consistencia eventual dentro del mismo request, aceptable para un evento manual).

### Option C: Seed por migración Flyway (+ runner)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja-media |
| Acoplamiento | Limpio |
| Escalabilidad | Mala para operación continua |
| Familiaridad | Alta (como `market_holiday`) |

**Pros:** bueno para backfill puntual reproducible.
**Cons:** no es operable en caliente; recompilar/migrar para cada evento; no es una "puerta" de uso.

## Trade-off Analysis

El nudo real es el **ciclo de módulos**, no la UI. Las opciones A-síncrona-en-marketdata lo violan; la B lo esquiva con el evento (misma dirección que ya existe, `transaction → marketdata`). La B además da **atomicidad correcta**: el registro del evento (fuente de verdad en el catálogo) commitea primero; el accrual (efecto derivado, idempotente) corre después y reintenta sin duplicar. Frente a C, la B es operable sin despliegues. El costo es “más piezas”, pero son exactamente las de dividendos (ya validadas), así que el patrón es conocido y barato de replicar.

## Consequences

- **Más fácil:** registrar un merger real y que impacte las carteras sin tocar código; sumar tender/bankruptcy/spinoff con el mismo molde; migrar a un feed automático después (solo cambia el productor, no el listener).
- **Más difícil / a vigilar:** hay que **exponer un `port.in` de registro en `marketdata`** (hoy solo expone `port-out`) o publicar el evento desde un servicio de aplicación de marketdata; el accrual es **AFTER_COMMIT** (eventual dentro del request) — el POST responde 202/registro y el efecto se materializa al commit; validación estricta del body (tipos de merger, ratio/cash según tipo) para no acreditar basura.
- **A revisitar:** si aparece un feed de corporate actions (p.ej. Massive `/corporate-actions`), el endpoint admin queda como override manual y el feed se vuelve el productor principal.

## Action Items

1. [ ] `marketdata`: `RegisterMergerUseCase` (port.in) + servicio que persiste `corporate_action(MERGER)`+`merger_action_detail` idempotente y publica `MergerDetectedEvent` (port-out).
2. [ ] `marketdata`: `MergerAdminController` `POST /api/v1/admin/corporate-actions/merger` (`ROLE_ADMIN`) + request DTO validado.
3. [ ] `transaction`: `MergerAccrualEventListener` (`@TransactionalEventListener` AFTER_COMMIT) → `MergerAccrualUseCase`.
4. [ ] `SecurityConfig`: `/api/v1/admin/corporate-actions/**` → `hasRole("ADMIN")`.
5. [ ] Tests: unit del use case de registro (idempotencia + publicación), slice/IT del endpoint (401 sin token, 403 sin ADMIN, 201/202 feliz), y e2e opcional (registrar merger → acreditar tenedor).
6. [ ] Exponer `marketdata.application.port.in` como named interface (o publicar el evento desde el servicio) sin romper `ModulithVerificationTest`.
7. [ ] Replicar el molde para tender/bankruptcy/spinoff (ADR-0014 F6c-3/F6d-3/F6e-3).
