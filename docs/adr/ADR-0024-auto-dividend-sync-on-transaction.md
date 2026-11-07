# ADR-0024: Auto-poblado de dividendos al registrar una compra (evento de integración en `shared`)

**Status:** Accepted
**Date:** 2026-11-06
**Deciders:** Aristeo (owner)
**Módulo:** `transaction` (publica el evento), `marketdata` (lo consume y puebla), `shared` (hospeda el tipo del evento)
**Relacionado:** [[ADR-0014]] (corporate actions framework), [[ADR-0015]] (ingesta admin + el nudo del ciclo `marketdata↔transaction`), [[ADR-0020]] (historia de dividendos inmutable / idempotente)

## Context

Tras ADR-0014 F3b y la ingesta multi-proveedor (Massive → FMP → AlphaVantage → DataBursatil → EODHD), el **motor** de dividendos está completo: proveedores, orquestador, persistencia en `corporate_action`/`dividend_action_detail` (idempotente), endpoint admin on-demand (`POST /api/v1/admin/corporate-actions/dividends/sync`) y el endpoint de crecimiento (`GET /api/v1/market/dividends/{symbol}/growth`).

Pero el **único disparador** del poblado es el endpoint admin. En la práctica eso produce dos fricciones reportadas en vivo:

1. El usuario registra una compra de un activo que paga dividendos (p. ej. GOOGL) → aparece el holding, pero **la pestaña de dividendos sale vacía**, como si no pagara. Registrar la transacción **no** dispara el poblado; "validado" significaba que el proveedor *puede* traer el dato, no que esté *poblado*.
2. El endpoint admin exige `ROLE_ADMIN` y **no existe ningún usuario admin** en la base, así que ni siquiera el poblado manual está disponible sin provisionar uno.

Se necesita que, al registrar una compra de un activo con dividendos, el historial **se pueble solo**, sin intervención manual ni rol admin.

**Fuerza de arquitectura clave (el nudo):** `transaction` **ya depende de** `marketdata` (escucha `DividendDetectedEvent` y `MergerDetectedEvent` de la named interface `port-out`). Por tanto, si `marketdata` dependiera a su vez de un tipo propiedad de `transaction`, se formaría un **ciclo `transaction↔marketdata`** que `ModulithVerificationTest` prohíbe. En ADR-0015 el flujo iba `marketdata → (evento) → transaction` (dirección permitida). Aquí el disparo nace en `transaction` y debe consumirlo `marketdata` — la dirección *contraria*.

## Decision

**`transaction` publica un evento de integración al registrar la compra; `marketdata` lo consume vía listener `@Async` + `AFTER_COMMIT` y llama al `SyncDividendsUseCase` existente. El tipo del evento vive en el módulo `shared` (OPEN) para evitar el ciclo.**

1. **`TransactionRegisteredEvent(String assetSymbol, String assetType)`** en `com.mx.cryptomonitor.shared.event`. Al vivir en `shared` (módulo `OPEN`), ambos módulos dependen solo de `shared` y **no entre sí** → sin ciclo. `assetType` viaja como `String` para no acoplar el enum de dominio de `transaction`.
2. **Publicación (`transaction`):** en `TransactionService.registerWithIdempotency` (punto único por el que pasan buy/sell/imported/generic), tras el registro exitoso y **dentro de la transacción**, se publica el evento **solo si** es una **COMPRA** (`BUY`) de un activo **elegible para dividendos** (`STOCK`/`ETF`). Publicar dentro de la transacción es lo que permite que el listener corra en `AFTER_COMMIT`.
3. **Consumo (`marketdata`):** `DividendSyncOnTransactionListener` (en `marketdata.infrastructure.inbound.event`), `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` + `@Transactional(REQUIRES_NEW)`, llama a `SyncDividendsUseCase.sync(symbol)`. El fallo del proveedor se captura y loguea: **nunca** revierte ni bloquea el registro del usuario.
4. **Idempotencia:** `sync` deduplica por `provider_event_id` y llave natural (ADR-0020), así que re-registrar el mismo símbolo no duplica dividendos.
5. El **endpoint admin se conserva** para backfill masivo / re-sync bajo demanda (ops). Los dos disparadores coexisten.

## Options Considered

### Option A: Evento en `transaction.application.port.out` expuesto como named interface

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Acoplamiento | **Rompe Modulith (ciclo)** |
| Familiaridad | Alta (igual que merger, pero al revés) |

**Pros:** simétrico con el evento de merger.
**Cons:** `marketdata` tendría que importar un tipo de `transaction`, y como `transaction → marketdata` ya existe, se crea el **ciclo** `transaction↔marketdata`. Además obligaría a exponer el paquete `port.out` de `transaction` (que hoy aloja puertos internos). Descartada.

### Option B: Evento en `shared` (OPEN); publica `transaction`, consume `marketdata` (ELEGIDA)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Acoplamiento | Sin ciclo (ambos → `shared`) |
| Escalabilidad | Buena (async, no bloquea) |
| Familiaridad | Alta (patrón de eventos Spring core ya usado) |

**Pros:** rompe el ciclo limpiamente; mínimo acoplamiento; el disparo interno **no requiere `ROLE_ADMIN`**; async → el registro responde al instante.
**Cons:** el tipo del evento no vive en el módulo "dueño" del concepto (compromiso del shared-kernel). Aceptable y documentado aquí.

### Option C: No auto-disparo; solo endpoint admin (status quo)

**Pros:** cero cambios. **Cons:** no cumple la expectativa del usuario; requiere admin inexistente; poblado manual perpetuo. Descartada.

### Option D: Disparo síncrono (sin `@Async`) dentro del registro

**Pros:** más simple. **Cons:** la llamada HTTP del usuario esperaría el round-trip al proveedor externo (latencia e inestabilidad del proveedor filtrándose al registro). Descartada a favor de `@Async`.

## Trade-off Analysis

El compromiso central es **dónde vive el tipo del evento**. Ponerlo en `shared` sacrifica pureza de propiedad (el concepto es de `transaction`) a cambio de romper el ciclo sin exponer puertos internos ni invertir dependencias artificialmente. Dado que `shared` es `OPEN` y ya es la base común, es el hogar natural para un **evento de integración** entre dos módulos que, de otro modo, se referenciarían mutuamente. `@Async` desacopla la latencia del proveedor del camino crítico del usuario; la idempotencia de `sync` hace inocuo el disparo repetido.

## Consequences

- **Más fácil:** registrar un STOCK/ETF puebla sus dividendos automáticamente; desaparece la dependencia de un usuario admin para el caso común.
- **Más fácil:** `shared.event` queda como lugar canónico para futuros eventos de integración que cruzarían en dirección cíclica.
- **Más difícil / a vigilar:** cada compra de un símbolo **nuevo** gatilla una llamada al proveedor externo (mitigado por idempotencia y, a futuro, un posible throttle "si se sincronizó hace < N días, saltar").
- **A revisar:** los holdings **ya registrados antes** de este cambio no se pueblan retroactivamente; requieren un re-registro o un backfill puntual por el endpoint admin.

## Action Items

1. [x] `TransactionRegisteredEvent` en `shared.event`.
2. [x] Publicación en `TransactionService` (BUY + STOCK/ETF, dentro de la transacción).
3. [x] `DividendSyncOnTransactionListener` en `marketdata` (`@Async` + `AFTER_COMMIT` + `REQUIRES_NEW`, fallo contenido).
4. [x] Tests unitarios (publisher + listener).
5. [ ] Verificar `ModulithVerificationTest` + ArchUnit en verde (parte del DoD).
6. [ ] (Opcional, P2) Throttle de re-sync por antigüedad; backfill de holdings preexistentes.
