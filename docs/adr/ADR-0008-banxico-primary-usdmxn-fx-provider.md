# ADR-0008: Banxico como proveedor primario del tipo de cambio USD/MXN (FIX), DataBursatil como fallback

**Status:** Accepted
**Date:** 2026-09-25
**Deciders:** Aristeo (owner backend)
**Módulo:** `marketdata` (FX)
**Relacionado:** [[ADR-0006]] (normalización FX en la valuación)

## Context

La valuación multi-moneda ([[ADR-0006]]) normaliza el costo MXN→USD con el tipo de cambio
USD/MXN. Hasta ahora, la única fuente del tipo de cambio era **DataBursatil** (`/v2/divisas`),
cacheada en `market_fx_snapshot` y leída por `CachedFxRateAdapter`.

El owner decide usar **Banxico SIE** como fuente **primaria** de referencia, por ser el dato
oficial: la serie **SF43718** — "Tipo de cambio Pesos por dólar E.U.A. para solventar obligaciones
denominadas en moneda extranjera, Fecha de determinación (**FIX**)", diaria. El endpoint de último
dato disponible es `GET /SieAPIRest/service/v1/series/SF43718/datos/oportuno` con header
`Bmx-Token`.

Ya existía infraestructura Banxico SIE en el módulo (`BanxicoRateAdapter` para la curva CETES, con
`banxicoWebClient` que ya inyecta `Bmx-Token` y parsea el shape `bmx.series[].datos[{fecha,dato}]`),
así que agregar la serie FX es incremental.

## Decision

**Banxico (FIX, SF43718) es el proveedor primario; DataBursatil (`/v2/divisas`) es el secundario
(fallback).** El orden se resuelve en `HybridQuoteService.getUsdMxnRate()`:

1. Intenta el primario vía el puerto neutral `UsdMxnFxRateProviderPort.fetchUsdMxnRate()`
   (implementado por `BanxicoRateAdapter`, reusando `fetchOportuno` + `latestRateForSeries`).
2. Si el primario devuelve dato → persiste el snapshot con `provider="BANXICO"` y lo devuelve.
3. Si el primario **no tiene dato o lanza excepción** (proveedor caído, token inválido, red) → cae
   a DataBursatil `/v2/divisas`, persiste con `provider="DATABURSATIL"` y lo devuelve.
4. Si ambos fallan → propaga la excepción de DataBursatil (comportamiento previo).

El resto del flujo de [[ADR-0006]] no cambia: el snapshot vive en `market_fx_snapshot` y
`CachedFxRateAdapter` sigue sirviéndolo (con refresco fetch-on-miss/stale, TTL
`marketdata.fx.cache-ttl-minutes`). El único cambio es **qué proveedor** llena el snapshot.

### Credenciales (protocolo de APIs)
- Token vía `${BANXICO_TOKEN}` (ya existente para la curva CETES), header `Bmx-Token`; base URL
  `${BANXICO_BASE_URL:https://www.banxico.org.mx}`. Sin hardcode.
- `BanxicoCredentialsIT` extendido: valida en vivo la serie FIX SF43718 (`fetchUsdMxnRate()`) además
  de la curva CETES, para detectar token falso/caducado o cambio de contrato.

## Options Considered

### Option A: Banxico primario + DataBursatil fallback detrás de un puerto neutral (elegida)
**Pros:** dato oficial FIX como referencia; sin regresión si Banxico está caído (fallback);
reusa la infra Banxico existente; blast radius mínimo (no toca `CachedFxRateAdapter` ni los
callers). **Cons:** el fallback DataBursatil queda inline (asimétrico frente al puerto); una
abstracción de lista de proveedores ordenada es refinamiento futuro.

### Option B: Reemplazar DataBursatil por Banxico (sin fallback)
**Cons:** un fallo de Banxico dejaría la valuación sin FX. **Rechazada.**

### Option C: Lista ordenada de `FxRateProviderPort` + servicio FX dedicado
**Cons:** más churn (nuevo servicio, mover `getUsdMxnRate` fuera de `HybridQuoteService`, reescribir
callers y tests recién validados). Buen v2 si se suman más proveedores. **Aplazada.**

## Consequences

- **FX de referencia** pasa a ser el FIX oficial de Banxico; DataBursatil respalda disponibilidad.
- El campo `provider` de `market_fx_snapshot` ahora puede ser `BANXICO` o `DATABURSATIL`.
- Banxico FIX solo aporta la tasa (sin `c`/`m`), así que `absChange`/`pctChange` quedan `null` en el
  snapshot cuando el proveedor es Banxico (columnas nullable; no se usan en la valuación).
- **A revisar (v2):** unificar primario y secundario tras `FxRateProviderPort` con lista ordenada
  (`@Order`) y un servicio FX dedicado; considerar FX FIX por fecha para costo histórico ([[ADR-0006]]).

## Action Items
1. [x] Puerto `UsdMxnFxRateProviderPort` (`fetchUsdMxnRate`, `providerName`).
2. [x] `BanxicoRateAdapter` implementa el puerto (serie SF43718 via `/datos/oportuno`).
3. [x] `HybridQuoteService.getUsdMxnRate()`: primario Banxico → fallback DataBursatil, persistencia
   con el proveedor ganador.
4. [x] Tests: `BanxicoRateAdapterTest` (FX FIX, header token, serie SF43718, vacío); `HybridQuoteServiceTest`
   (primario Banxico, fallback por vacío, fallback por excepción).
5. [x] `BanxicoCredentialsIT` valida en vivo SF43718.
6. [ ] Ejecutar `mvn test -Dtest=*CredentialsIT` con `BANXICO_TOKEN` real antes de dar por cerrada la
   integración (DoD del protocolo de APIs).
