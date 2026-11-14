# ADR-0029: Read-model de ingresos por dividendo del usuario (historial "lo que recibí")

**Status:** Accepted
**Date:** 2026-11-07
**Deciders:** Aristeo (owner)
**Módulo:** `transaction` (read-model + endpoint), `marketdata` (origen de los campos de presentación vía accrual)
**Relacionado:** [[ADR-0014]] (corporate actions / dividend detail), [[ADR-0024]] (auto-poblado), [[ADR-0027]] (accrual per-usuario / late joiner), cambio reciente en `/me/transactions` (excluye `DIVIDEND` del listado de operaciones)

## Context

Hoy los pagos de dividendos se registran como filas `transaction` con `transactionType=DIVIDEND` (el accrual de ADR-0024/0027 calcula el monto real del usuario: sus acciones × dividendo/acción en la fecha ex). Esas filas **se mezclaban** en la vista de Transacciones, lo que confunde (una transacción es una operación: compra/venta/transfer, no un ingreso). Ya se corrigió que `GET /api/v1/me/transactions` **excluya** `DIVIDEND` por defecto, dejando la vista de operaciones limpia (paridad con la pestaña "Trades" de Snowball).

Pero los dividendos recibidos siguen siendo información valiosa que el usuario quiere ver — **como su propia tabla**, estilo la sección "Dividends" de Investing.com (Fecha ex · Dividendo · Tipo · Fecha de pago), pero con **sus montos**, no el histórico genérico por-acción del símbolo.

Dos lecturas distintas del mismo hecho "dividendo":
- **Referencia de mercado** (genérica, por símbolo): todo el histórico por-acción que pagó GOOGL, igual para cualquier usuario. Es lo que expone (o expondría) un endpoint `/market/dividends/{symbol}/...` y lo que muestra Investing. **No** es lo que el usuario pidió para su cartera.
- **Ingreso del usuario** (user-scoped): solo los dividendos que **él** recibió — sus acciones × dividendo/acción, únicamente en su periodo de tenencia, con retención y neto. Es lo profesional y práctico para "Mis Dividendos".

El dato del segundo ya existe y está calculado: son precisamente las transacciones `DIVIDEND` del usuario + su `DividendDetail` (que ya tiene `exDividendDate`, `dividendType`, `taxWithheld`). No hay que recalcular entitlement.

## Decision

**Exponer un read-model user-scoped de ingresos por dividendo**, `GET /api/v1/me/dividends`, en el módulo `transaction` (donde vive el dato), que lee las transacciones `DIVIDEND` del usuario + su `DividendDetail` y las presenta como la tabla "Mis Dividendos" (estilo Investing, con montos del usuario). La fila del ledger `DIVIDEND` **permanece** (alimenta Total Return y el accrual); solo se **presenta** como ingreso, no como transacción. Mismo dato, dos lecturas.

### Contrato propuesto

```
GET /api/v1/me/dividends        (ROLE_USER; filtrado a los holdings del usuario por el backend)
→ [
    {
      "symbol": "GOOGL",
      "assetName": "Alphabet Inc. Class A",
      "exDate": "2026-06-16",
      "paymentDate": "2026-06-15",
      "shares": 1,
      "amountPerShare": 0.20,
      "grossAmount": 0.20,
      "taxWithheld": 0.00,
      "netAmount": 0.20,
      "type": "CASH",            // forma: CASH | STOCK
      "frequency": 4,            // pagos/año (4=trimestral, 12=mensual, 1=anual); null si no se conoce
      "distribution": "RECURRING", // RECURRING | SPECIAL
      "currency": "USD"
    }
  ]
```

Orden: `paymentDate` descendente (lo más reciente primero).

### Reglas

1. **Solo recibidos:** filtrar `paymentDate <= hoy`. Un dividendo con pago futuro **aún no se recibió**; esos ya salen en "Próximo pago" del resumen (/growth, ADR-0026). Esto también sana el efecto colateral de que hoy se acreditan dividendos con `paymentDate` futura como ingreso.
2. **User-scoped garantizado por el backend:** el endpoint resuelve el `userId` del JWT y solo devuelve las filas `DIVIDEND` de ese usuario. No existe modo de pedir dividendos de símbolos ajenos (a diferencia de un `/market/{symbol}`).
3. **Self-contained (sin cruce de módulos en el hot path):** los campos de presentación que hoy viven en marketdata (`amountPerShare`, `frequency`, `distribution`) se **denormalizan sobre `DividendDetail`** en el momento del accrual (opción (b) de la discusión), para que la lectura sea una sola consulta en `transaction`, sin port a marketdata por fila.

## Options Considered

### Option A: Endpoint de mercado por símbolo (`/market/dividends/{symbol}/history`)
| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja (clona el patrón de `/growth`) |
| Scope usuario | Solo por convención (el front llama solo a símbolos poseídos) |
| Qué muestra | Dividendo **por acción** genérico, no lo recibido |
| Llamadas | 1 por holding (fan-out en el front) |

**Pros:** mínimo esfuerzo, cacheable, reutiliza el modelo acíclico. **Cons:** no es "lo que recibí" — repite el histórico completo del símbolo (incluidos dividendos previos a la compra); no conoce acciones, retención ni neto del usuario. Es data de referencia, no un estado de cuenta. **Descartada como vista principal** (puede existir aparte para la referencia de mercado).

### Option B: Read-model user-scoped (`/me/dividends`) — ELEGIDA
| Dimensión | Evaluación |
|---|---|
| Complejidad | Media (lee tx DIVIDEND + detail; migración chica para denormalizar 3 campos) |
| Scope usuario | Garantizado por el backend (seguro) |
| Qué muestra | Lo que **el usuario recibió** (acciones × div, bruto/retención/neto, su periodo) |
| Llamadas | **1 sola** para toda la cartera |

**Pros:** semántica correcta de "Mis Dividendos"; una llamada; reusa el accrual ya calculado; abre la puerta a totales de ingreso/año. **Cons:** pide denormalizar 3 campos de presentación sobre `DividendDetail` (migración + ajuste del accrual para que el evento/listener los traiga).

### Option C: Reusar `/me/transactions?transactionType=DIVIDEND`
El filtro explícito ya devuelve las filas `DIVIDEND`. **Cons:** `TransactionResponse` no incluye `exDividendDate`, `taxWithheld`, `dividendType`, `frequency`, `distribution` → el front tendría que llamar `/details/{id}` por fila (chatty) y aún así faltan `frequency`/`distribution`. Mezcla el contrato de "operación" con el de "ingreso". **Descartada**: el read-model dedicado es más limpio.

## Trade-off Analysis

El eje es "referencia de mercado vs estado de cuenta del usuario". La tabla que el usuario pidió es un **estado de cuenta** (lo que cobró), no un catálogo del símbolo; eso obliga a un read-model user-scoped (Opción B), no a un endpoint de mercado (A). Reusar el listado de transacciones (C) ahorra un endpoint pero ensucia el contrato y queda corto de campos. El costo de B es acotado: el dato per-usuario ya existe (no se recalcula), y lo único nuevo es denormalizar 3 campos de presentación para que la lectura no cruce módulos por fila. Se elige **B**.

## Consequences

- **Más fácil:** "Mis Dividendos" en Portfolio>Dividendos sale de una llamada, con montos reales, retención y neto; los dividendos dejan de ensuciar Transacciones (ya hecho) y tienen su hogar correcto.
- **Más difícil / a vigilar:** el accrual debe poblar `amountPerShare`, `frequency` y `distribution` en `DividendDetail` (migración + el evento `DividendDetectedEvent`/listener debe acarrear esos campos desde el `dividend_action_detail` de marketdata). Los dividendos ya acreditados **antes** de la migración quedarían con esos campos en null hasta un backfill (aceptable: la tabla degrada mostrando "—" en Tipo).
- **A revisar:** el "Rendimiento" por pago (dividendo ÷ precio a la fecha ex) queda **fuera de v1** (requiere precio histórico); se añade después si se quiere paridad total con Investing. Un posible `/market/dividends/{symbol}/history` (referencia genérica) puede añadirse aparte si se necesita el histórico completo del símbolo, sin mezclarlo con este read-model.

## Action Items

### v1 (HECHO)
1. [x] (migración) `V2026_11_07_02__dividend_detail_amount_per_share.sql`: columna `amount_per_share` (nullable) en `dividend_detail`. Checksum registrado (`3743077776L`).
2. [x] (accrual) Propagar `amountPerShare` al crear la tx `DIVIDEND`: `DividendTransactionRequest` gana `amountPerShare`, `DividendAccrualService` pasa el `dividendPerShare` (ya lo tenía el evento, sin tocar el contrato cross-módulo), y `registerDividendTransaction` lo persiste en `DividendDetail`.
3. [x] (read-model) `GetUserDividendsUseCase` + `UserDividendQueryService` (transaction): `DividendDetailRepository.findUserDividends(userId, cutoff)` (JOIN FETCH tx, `transactionDate <= hoy`, orden pago desc) → `UserDividendResponse` (bruto/retención/neto, acciones = bruto/div-acción, nombre+logo del catálogo).
4. [x] (REST) `GET /api/v1/me/dividends` (`UserDividendController`); matcher `"/api/v1/me/dividends/**".hasRole("USER")` en `SecurityConfig`. Cache-Control private 5m.
5. [x] (tests) `UserDividendQueryServiceTest` (mapeo bruto/retención/neto/acciones, shares null sin div-acción, vacío). ITs `DividendAccrualFlowIT`/`DividendTransactionIT` + Modulith/ArchUnit verdes.

### v2 (follow-up, fuera de v1)
6. [ ] Columna "Tipo" completa: denormalizar `frequency` + `distribution_type` sobre `dividend_detail` — requiere **extender `DividendDetectedEvent`** (añadir `frequency`, `distributionType`) y que `DividendSyncService` los publique desde `dividend_action_detail`; el listener y `accrue(...)` los pasan. Hoy "Tipo" expone solo la forma `CASH`/`STOCK`.
7. [ ] Backfill de `amount_per_share` (y v2) para dividendos acreditados antes de la migración (hoy degradan a "—").
8. [ ] Rendimiento por pago (necesita precio histórico a la fecha ex); totales de ingreso por año; opcional `/market/dividends/{symbol}/history` para la referencia genérica.
