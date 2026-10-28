# ADR-0001: Registrar la serie Banxico SIE `SF60633` (CETES) como fuente de datos

**Status:** Proposed
**Date:** 2026-07-24
**Deciders:** Aristeo (owner backend) + quien valide el significado económico de la serie
**Módulo:** `marketdata`

## Context

- El proveedor **Banxico SIE** ya está integrado: `BanxicoRateAdapter implements GovBondRatePort`,
  cacheado por `BanxicoCurveService` (Redis → Postgres → proveedor, refresco `@Scheduled` semanal).
  Ese servicio **ya es** el patrón poller + vista materializada (CQRS read model).
- El adaptador hoy pide **5 series** (curva CETES primaria) en un solo `/oportuno`:
  `SF43936, SF43939, SF43942, SF43945, SF349785`.
- La serie solicitada, `SF60633`, es **CETES 28 días** — pero el plazo de 28d ya está cubierto por
  `SF43936`. Por tanto `SF60633` **no es un plazo faltante de la curva**, sino *otra medida* del
  mismo plazo (tasa de referencia/secundaria distinta a la de subasta primaria). **Este punto debe
  confirmarlo el equipo de datos** (igual que el comentario `// confirmado por el equipo de datos`
  ya presente en el adapter).
- Ya existe una **segunda fuente** de CETES vía DataBursatil `/v2/tasas` (`getRates()`). Hay riesgo
  de solapamiento y drift entre fuentes.
- El path `/SieAPIRest/service/v1/series/{ids}/datos/oportuno` acepta `{ids}` separados por coma,
  así que una serie extra puede viajar en el **mismo batch** que la curva: 0 llamadas adicionales.

## Decision

Modelar `SF60633` como una **tasa escalar distinta de la curva**, exponerla con un **método nuevo en
`GovBondRatePort`** reutilizando el adaptador, el `banxicoWebClient` (token `Bmx-Token`) y la caché
existentes.

Regla de decisión sobre dónde cae la serie:

| Si `SF60633` es…                                    | Entonces…                                     |
| --------------------------------------------------- | --------------------------------------------- |
| Otro plazo de la **misma curva primaria**           | Agregar a `SERIES_BY_TERM` (Opción A)         |
| Una **medida distinta** del CETES 28d (referencia)  | Método aparte, no toca la curva (Opción B) ✅ |

## Options Considered

### Opción A: Añadir `SF60633` al mapa `SERIES_BY_TERM`

| Dimensión           | Evaluación             |
| ------------------- | ---------------------- |
| Complejidad         | Baja                   |
| Costo (calls)       | 0 (entra en el batch)  |
| Escalabilidad       | Mala si no es un plazo |
| Familiaridad equipo | Alta                   |

**Pros:** cambio de una línea; se cachea y persiste sin tocar nada más.
**Cons:** el mapa es `Map<Integer termDays, seriesId>`; `28` ya apunta a `SF43936`, así que
colisionaría o falsearía la curva. Solo válido si de verdad es un plazo que hoy no existe.

### Opción B: Método nuevo en `GovBondRatePort`, reusando adapter + caché ✅

| Dimensión           | Evaluación                    |
| ------------------- | ----------------------------- |
| Complejidad         | Baja-media                    |
| Costo (calls)       | 0 (se une al batch `/oportuno`) |
| Escalabilidad       | Buena                         |
| Familiaridad equipo | Alta                          |

Refactor: extraer `fetchOportuno(String seriesIds)` como primitiva (hoy hardcodeada a los 5 ids),
y exponer `Optional<BigDecimal> getReferenceRate()`. Cachearla en `BanxicoCurveService` bajo una
clave Redis propia, con cadencia acorde a su frescura (CETES 28d se actualiza en días hábiles).

**Pros:** respeta el hexagonal; no toca la curva; cero llamadas nuevas; testeable clonando
`BanxicoRateAdapterTest`.
**Cons:** un método más en el puerto; hay que decidir su TTL/cadencia (diaria vs. semanal de la
subasta).

### Opción C: Generalizar a un puerto "serie SIE" genérico

| Dimensión           | Evaluación         |
| ------------------- | ------------------ |
| Complejidad         | Media-alta         |
| Costo (calls)       | 0-bajo (batch)     |
| Escalabilidad       | Excelente          |
| Familiaridad equipo | Media              |

`BanxicoSieSeriesPort.getLatest(Set<String> seriesIds) -> Map<String, SieDataPoint>`. Trata SIE como
proveedor genérico de time-series (TIIE, UDIS, inflación, FX viven todos ahí). La curva CETES pasa a
ser *un consumidor* de esta primitiva.

**Pros:** una sola abstracción para todo Banxico SIE; añadir series futuras es trivial.
**Cons:** cambia la abstracción de dominio ("curva CETES" → "series SIE"); sobre-ingeniería si
`SF60633` es lo único en el horizonte.

## Trade-off Analysis

El eje real **no es el consumo de API** — las tres cuestan 0 llamadas extra si `SF60633` viaja en el
batch `/oportuno` que ya se hace. El eje es la **claridad del modelo de dominio**:

- **A** miente sobre lo que es la curva. Rechazada salvo que sea un plazo genuino.
- **C** es correcta a largo plazo pero paga estructura por una sola serie hoy.
- **B** es el punto medio: modela `SF60633` como lo que es, reutiliza todo, y deja la primitiva
  `fetchOportuno(ids)` extraída — primer paso hacia C si mañana llegan más series.

**Recomendación: Opción B**, dejando la primitiva por-ids extraída para evolucionar a C sin refactor
grande.

## Consequences

**Más fácil:**

- Consumir la tasa de referencia CETES 28d sin sumar carga a Banxico.
- Migrar a C después: la primitiva `fetchOportuno(ids)` ya quedaría lista.

**Más difícil / a vigilar:**

- **Fuente de verdad de CETES.** Hay tres toques (curva SIE, `/v2/tasas` de DataBursatil, y
  `SF60633`). Declarar: **Banxico SIE = autoritativo** (`CetesRateTableService` ya lo asume);
  DataBursatil `/tasas` queda como conveniencia/fallback o solo para TIIE/objetivo.
- **Cadencia.** La curva refresca `@Scheduled` semanal (subasta martes). `SF60633` como referencia
  diaria necesita su propia cadencia/TTL — no colgarla del cron semanal si se espera frescura diaria.
- **Secreto.** `Bmx-Token` ya está externalizado en `BanxicoProperties`. Reafirmar: nunca loguear el
  token.

## Action Items

1. [ ] Confirmar con el equipo de datos qué es económicamente `SF60633` (medida vs. plazo) → fija A vs. B.
2. [x] Extraer `fetchOportuno(String seriesIds)` como primitiva en `BanxicoRateAdapter`.
3. [ ] Decidir si `SF60633` viaja en el batch de la curva (0 calls, cadencia semanal) o en su propia
       petición (cadencia diaria).
4. [x] Añadir método al `GovBondRatePort` (`getReferenceRate()`), con su mapeo.
5. [ ] Cachear en `BanxicoCurveService` con clave Redis propia y cadencia acorde a su frescura.
6. [ ] Declarar y documentar la fuente de verdad de CETES entre las 3 fuentes.
7. [x] Tests unitarios en `BanxicoRateAdapterTest`; integración en `BanxicoDataControllerIT`.
