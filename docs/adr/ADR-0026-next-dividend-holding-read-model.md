# ADR-0026: Exposición del "próximo dividendo" como read-model por holding

**Status:** Accepted
**Date:** 2026-11-06
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (lectura del catálogo de corporate actions + proyección a respuesta)
**Relacionado:** [[ADR-0025]] (detección de elegibilidad + badge en `/growth`), [[ADR-0024]] (auto-poblado al registrar), [[ADR-0014]] (corporate actions framework), [[ADR-0020]] (historia de dividendos inmutable)

## Context

La vista "Dividends" de referencia (Snowball Analytics) muestra, por holding y **sin que el usuario declare nada**, una fila con: dividend growth (5Y), yield, dividendos recibidos, **fecha del próximo pago**, **monto del próximo pago por acción** y **ex-dividend date**. Nuestro sistema ya cubre el flujo "sin declaración" (ADR-0024) y el growth/estado (ADR-0025 + F4-A). El **único hueco** para la paridad es exponer el **próximo dividendo** (próximo ex-date / pago / monto).

Esa data **ya se captura y persiste**: los proveedores devuelven el dividendo futuro anunciado (Massive/FMP para US — p. ej. GOOGL ex 2026-12-07; DataBursatil `reciente` con `fechaexcupon` para BMV), y `DividendSyncService` lo guarda como `corporate_action` con estado `ANNOUNCED` + su `dividend_action_detail` (monto, moneda, frecuencia). Falta **solo la exposición** en el contrato.

**Fuerza de arquitectura clave:** `GET /api/v1/market/dividends/{symbol}/growth` **ya no es un recurso REST normalizado** — es un **read-model orientado a vista**: devuelve una serie anual agregada + CAGR calculada + moneda + (F4-A) `eligibilityStatus`/`lastCheckedAt`, todo denormalizado para pintar la fila de un holding en una sola llamada. El proyecto **ya tomó la decisión** de servir los datos de dividendos como read-model por holding. La pregunta es si el "próximo dividendo" sigue ese patrón o abre uno nuevo.

## Decision

**Incorporar el próximo dividendo como un objeto `nextDividend` dentro de `/growth`, consolidándolo como el read-model de dividendos por holding** (la única llamada por holding que alimenta toda la fila de la tabla). Aditivo, consistente con F4-A.

```json
"nextDividend": {
  "exDividendDate": "2026-12-07",
  "paymentDate":    "2026-12-14",
  "amountPerShare": 0.22,
  "currency":       "USD",
  "frequency":      4
}   // null si no hay un dividendo futuro anunciado
```

Se deriva del `corporate_action` DIVIDEND del símbolo con `pivotal_date` (ex-date) futura más próxima y estado no pagado (`ANNOUNCED`/`EFFECTIVE`), uniendo su `dividend_action_detail`. **Lectura pura; no toca la ingesta ni el modelo.** Como limpieza futura, el endpoint se renombrará/aliasará a un canónico `GET /market/dividends/{symbol}` (su nombre `/growth` ya no describe todo lo que devuelve).

## Options Considered

### Option A: `nextDividend` dentro de `/growth` (read-model por holding) — ELEGIDA

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Round-trips cliente | **1 por holding** (la fila completa) |
| Consistencia con el repo | **Alta** (mismo patrón que F4-A y que el propio `/growth`) |
| Cohesión | Alta (toda la info de dividendos del holding, junta) |
| Pureza REST | Media (es un read-model, no un recurso normalizado) |

**Pros:** una sola llamada por holding pinta la fila entera (menos round-trips = mejor UX de la tabla); consistente con la decisión ya tomada en F4-A; cohesivo; aditivo (no rompe el contrato).
**Cons:** `/growth` queda como nombre impreciso (mitigable con alias canónico); payload algo mayor para un widget que solo quiera el calendario (mitigable con selección de campos a futuro).

### Option B: Endpoint dedicado `GET /market/dividends/{symbol}/next`

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Round-trips cliente | **2 por holding** (growth + next) → N×2 en la tabla |
| Consistencia con el repo | Media (reintroduce estilo REST granular que F4-A ya abandonó) |
| Cohesión | Media (parte la info del holding en dos llamadas) |
| Pureza REST | Alta (sub-recurso de responsabilidad única) |

**Pros:** separación de responsabilidades limpia; cache independiente; sub-recurso sibling de `/growth`.
**Cons:** **chatty** para la tabla (duplica las llamadas por fila); incoherente con F4-A (si el estado va en `/growth`, ¿por qué el próximo pago no?); optimiza la pureza REST sobre la realidad (la tabla es una vista, no un grafo de recursos).

### Option C: Read-model batch por usuario (`GET /me/portfolio/dividends`)

| Dimensión | Evaluación |
|---|---|
| Complejidad | **Alta** |
| Round-trips cliente | 1 para toda la tabla |
| Consistencia con el repo | Media (nuevo patrón, requiere refactor del front ya integrado) |
| Escalabilidad | Alta |

**Pros:** lo más eficiente para la tabla (una llamada); el "mejor estándar" para servir una vista rica (CQRS read-model / BFF).
**Cons:** el front **ya integró** `/growth` por holding (PR#9) → refactor; mayor esfuerzo; prematuro para 4 holdings. Es la **evolución de escala** natural, no el paso de ahora.

## Trade-off Analysis

El eje real no es "REST puro vs pragmático", sino **"¿qué estilo ya eligió el proyecto para los datos de dividendos?"**. `/growth` es, de hecho, un read-model por holding (denormalizado, orientado a la fila). La Opción B reintroduciría un estilo granular que F4-A ya descartó al meter el estado en `/growth`, partiendo la fila en dos llamadas — menos cohesión y más round-trips sin ganancia real (la "pureza REST" no es un requisito del cliente; una fila de tabla sí lo es). La Opción C es estrictamente el estándar más alto para servir una tabla (un read-model/BFF dedicado), pero choca con lo ya integrado y es desproporcionada para la escala actual; queda como camino de escala. **La Opción A es la decisión profesional porque es la consistente con la arquitectura ya adoptada**: un read-model cohesivo por holding, de una sola llamada, aditivo y sin deuda nueva — y deja la puerta abierta a C cuando la tabla lo justifique. La consistencia con una decisión deliberada previa es, en sí, un estándar de ingeniería.

## Consequences

- **Más fácil:** el front pinta la fila completa de "Dividends" (growth, estado, próximo pago, ex-date) con **una** llamada por holding que ya hace.
- **Más fácil:** paridad con la referencia (Snowball) sin pedirle al usuario declarar nada; cierra el flujo auto-detect de punta a punta.
- **Más difícil / a vigilar:** el nombre `/growth` queda impreciso → se agenda un **alias canónico `GET /market/dividends/{symbol}`** (el viejo queda como alias deprecado) para no romper al front.
- **A revisar:** si la cartera crece mucho, migrar la tabla a un read-model batch (Opción C) usando esta proyección como base.

## Action Items

1. [x] Query en `CorporateActionRepository`: próximo DIVIDEND por símbolo con `payment_date >= hoy` (pago no realizado), el más cercano. Su detalle vía `DividendActionDetailRepository.findById`.
2. [x] `DividendGrowthService` proyecta `nextDividend` (o `null`) en `DividendGrowthResponse`. Lectura pura.
3. [x] Tests unitarios: próximo anunciado se expone; sin futuro → `null`.
4. [ ] Avisar al front el contrato de `nextDividend`.
5. [ ] (Follow-up) Alias canónico `GET /market/dividends/{symbol}`; deprecar el nombre `/growth`.
6. [ ] (Escala) Read-model batch por usuario si la tabla lo requiere.
