# ADR-0016: Proveedores de precio "de hoy" multi-mercado para acciones (EODHD unificado USD+MXN; api-ninjas/databursatil como alternos), fin del rezago de día previo

**Status:** Accepted
**Date:** 2026-10-23
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (proveedores `StockQuoteProvider` + orquestador), toca `portfolio` (valuación) y `shared` (cache TTL)
**Relacionado:** [[ADR-0002]] (CredentialsIT para proveedores de market data), [[ADR-0008]] (catálogo single-source-of-truth de metadata de activo), [[ADR-0009]] (trade-date FX), [[ADR-0010]] (moneda de presentación), [[ADR-0012]] (calendarios de mercado NYSE/BMV). Habilita el futuro **ADR de modelo de moneda funcional** (costo base real en MXN para activos comprados en MXN).

## Context

`StockQuoteOrchestrator.getLatest(symbol)` alimenta la valuación de holdings (`PortfolioService.calculateMarkedValue`, `PriceUpdateService`, `/me/portfolio/summary`). Hoy la cadena es **Twelve Data → Alpha Vantage → Massive/Polygon** (`MarketDataProviderConfig`).

**Problema comprobado (23.10.2026):** en los planes free que tenemos, estos proveedores **no entregan el precio de hoy**:
- Massive/Polygon: en nuestro plan solo da hasta el **cierre del día anterior** (`/v2/aggs/.../prev` devolvió el 22.10; `snapshot`, `last/trade` y rangos recientes dan `403 NOT_AUTHORIZED`).
- Alpha Vantage / Twelve Data: cuota diaria muy baja (25–800/día), lo que forzó un TTL de caché de **24h** ([[project_stockprice_cache_staleness]]) → el "precio actual" puede quedar congelado casi un día. Para nombres volátiles (MELI) deriva ~1-2% intermitente.

**Insumos nuevos disponibles (validados en vivo), todos con precio de HOY:**
- **EODHD** `GET /api/real-time/{SIMBOLO}?api_token=…&fmt=json`: **quote real-time multi-mercado por sufijo**. `MELIN.MX` → **BMV en MXN** (`close 33,848.82`, `previousClose 33,723.58`, `change_p 0.37%`, OHLC, `volume`, `timestamp` 2026-10-23 19:58 UTC); `MELI.US` → NASDAQ en USD. Un solo contrato cubre ambos mercados y ya trae `previousClose`/`change` (Day change nativo).
- **api-ninjas** `GET /v1/stockprice?ticker=MELI` (header `X-Api-Key`): NASDAQ **USD**, `price 1810.15`, `updated` = cierre de hoy, `previous_close`+`change`. Solo US.
- **databursatil** `GET /v2/intradia?...&emisora_serie=MELIN&bolsa=BMV&token=…`: serie **intradía MXN** de BMV/SIC (último punto 23.10 = 33,890 MXN). Solo MX, formato serie.

**Triangulación (1 acción MELI, 23.10):** NASDAQ USD api-ninjas 1,810.15 ≈ investing.com 1,810.15 ≈ caché 1,807.96 (−0.12%); BMV MXN EODHD 33,848.82 ≈ databursatil 33,890 (<0.2%); FX implícita 33,848.82÷1,810.15 = 18.70 ≈ USD/MXN en Redis 18.698. **Todas las fuentes concuerdan dentro de ~0.25%.** El lado del *valor* es sólido; lo que faltaba era que `getLatest` tomara **hoy**, y —para posiciones SIC— que lo tomara **en MXN nativo**.

## Decision

Adoptar **EODHD como proveedor primario unificado de "precio de hoy"** bajo el puerto `StockQuoteProvider` y el orquestador de fallback ya probados, resolviendo el **símbolo por mercado del holding**:

- US-listado → `{TICKER}.US` (USD).
- BMV/SIC → `{EMISORA_SERIE}.MX` (MXN nativo; p. ej. `MELI`→`MELIN.MX`).

Un solo adapter + una credencial + un contrato cubren ambos mercados, con `previousClose`/`change` para el Day change. **api-ninjas (US/USD)** y **databursatil (BMV/MXN)** quedan como **alternos de fallback y verificación cruzada**; la cadena actual (Twelve Data/Alpha Vantage/Massive) pasa al final.

**Fases:**
- **Fase 1 (quick win, sin decisión de diseño pendiente):** EODHD `.US` como primario USD delante de la cadena actual + **bajar `app.cache.stock-prices-ttl` de `PT24H` a `PT15M`**. Fija la frescura ya para todas las acciones US (incluida la pata USD de MELI).
- **Fase 2 (habilita moneda funcional MXN):** rutear posiciones SIC/BMV a EODHD `.MX` (valor en MXN nativo, sin proxy NASDAQ×FX). Requiere mapeo de símbolo→(mercado, sufijo, moneda) en el catálogo ([[ADR-0008]]) e incluir mercado/moneda en la key de caché.

**Gate técnico:** confirmar vía `EodhdCredentialsIT` que nuestro plan EODHD entrega **real-time para `.MX` y `.US`** (no solo EOD/US); si `.MX` no estuviera cubierto en real-time, databursatil cubre el lado MXN de la Fase 2.

**Alcance explícito:** este ADR decide **de dónde** sale el precio y **en qué moneda** se cotiza cada posición. **NO** decide el modelo de costo base (si el costo de un activo comprado en MXN se muestra como los pesos realmente pagados vs. re-expresado vía USD). Esa distorsión ([[project_mxn_costbasis_fx_distortion]]) se resuelve en un ADR aparte de **moneda funcional**, que la Fase 2 habilita pero no implica.

## Options Considered

### Option A: Solo api-ninjas (primario USD), resto fallback
| Dimensión | Evaluación |
|-----------|------------|
| Complejidad | Baja |
| Cobertura | Solo US/USD |
| Frescura | Precio de hoy (NASDAQ) |
| Fidelidad MXN | Sigue siendo NASDAQ×FX (proxy) |

**Pros:** mínimo cambio; arregla el rezago US ya. **Cons:** no valúa nativo en MXN; no habilita moneda funcional.

### Option B: Dual-source api-ninjas (USD) + databursatil (MXN)
| Dimensión | Evaluación |
|-----------|------------|
| Complejidad | Media-alta (dos contratos, formatos distintos; serie en databursatil) |
| Cobertura | US + BMV |
| Fidelidad MXN | Nativa |

**Pros:** ambos mercados nativos. **Cons:** **dos** integraciones heterogéneas; databursatil entrega serie (parseo del último punto), sin `change` directo.

### Option C: EODHD unificado multi-mercado — **recomendada**
| Dimensión | Evaluación |
|-----------|------------|
| Complejidad | Media (un adapter; mapeo símbolo→sufijo/mercado) |
| Cobertura | **US + BMV (y global) en un contrato** |
| Frescura | Precio de hoy en ambos |
| Fidelidad MXN | **Nativa** (`.MX` en MXN) |
| Extras | `previousClose`/`change`/OHLC directos |

**Pros:** una sola integración para los dos mercados; quote limpio real-time; Day change gratis; cuadra con GBM en MXN. **Cons:** dependemos de un proveedor para ambos mercados (mitigado con api-ninjas/databursatil como fallback); hay que confirmar cobertura real-time `.MX` en el plan.

### Option D: Status quo + solo bajar TTL a PT15M — **descartada**
**Cons:** **no funciona** — Massive no entrega hoy en nuestro plan; un TTL corto solo re-consultaría la misma fuente rezagada. Descartada por los `403` observados.

## Trade-off Analysis

La frescura es el problema inmediato y cualquier fuente de hoy (A/B/C) lo resuelve. La diferencia está en **cobertura + fidelidad MXN + costo de mantenimiento**: EODHD (**Option C**) logra ambos mercados con **una** integración y formato limpio, mientras B exige dos contratos heterogéneos y A no da MXN nativo. El riesgo de concentrar en un proveedor se mitiga dejando **api-ninjas** y **databursatil** como fallback/verificación cruzada —que además son excelentes para los `*CredentialsIT`. Única incógnita de C: que el plan EODHD cubra real-time `.MX`; por eso el gate en el CredentialsIT antes de hacerlo primario del lado MXN.

Riesgo transversal (B y C, Fase 2): el **mapeo de símbolo** global↔mercado (ticker→`MELIN.MX`/`emisora_serie`+`bolsa`) y saber en qué mercado vive cada holding. Se resuelve extendiendo el catálogo ([[ADR-0008]]), no en el adapter.

## Consequences

**Más fácil:**
- El "valor actual" refleja **hoy**; desaparece la deriva intermitente de hasta 24h.
- `previousClose`/`change` habilitan el "Day change" per-holding sin cálculo extra.
- (Fase 2) posiciones SIC valuadas en **MXN real** → base para que el P&L cuadre con GBM.
- Con EODHD, una sola integración en vez de dos.

**Más difícil / a vigilar:**
- Dependencia de EODHD para ambos mercados (mitigada con fallback).
- Mapeo catálogo global↔mercado y resolución por holding (Fase 2).
- **Secreto en logs:** EODHD y databursatil pasan el token por query param (`?api_token=`, `?token=`); NUNCA loggear la URL con token (misma lección que `MassiveAdapter`; respetar `AuthNoSecretsInLogsUnitTest`).
- El `@Cacheable` de `getLatest` usa `#symbol` como key; al rutear por mercado, la key debe incluir mercado/moneda para no mezclar el USD y el MXN del mismo ticker.

**A revisitar:**
- El ADR de **moneda funcional** (costo base real en MXN) — este ADR lo habilita pero no lo decide.
- Consolidar la fuente de la tasa USD/MXN "actual" ([[project_mxn_costbasis_fx_distortion]] #3): `fx_rate_daily` más reciente estaba a ~1 mes.

## Action Items

### Fase 1 — EODHD `.US` (USD, hoy) + TTL
1. [ ] `EodhdProperties` (`base-url`, `api-token`) leídas con `@Value`/`@ConfigurationProperties` desde `application.yml`; **sin** credenciales hardcoded (protocolo CLAUDE.md).
2. [ ] `EodhdAdapter implements StockQuoteProvider` (`getLatest` → `close`; token por config; `429`→`ExternalProviderRateLimitException`, símbolo inválido→`ExternalProviderInvalidSymbolException`; **nunca** loggear la URL con token).
3. [ ] Bean `@ConditionalOnExpression` (base-url + token presentes), **primero** en el orden del `stockQuoteOrchestrator`.
4. [ ] `EodhdCredentialsIT` en `com.mx.cryptomonitor.integration.marketdata` ([[ADR-0002]]): llamada real que valide token vigente + contrato JSON (`close`, `previousClose`, `change`, `timestamp`) **y el gate real-time `.MX`+`.US`**.
5. [ ] Bajar `app.cache.stock-prices-ttl` a `PT15M` en `application.yml`.
6. [ ] (Opcional) `ApiNinjasAdapter` + `ApiNinjasCredentialsIT` como fallback USD/verificación cruzada.
7. [ ] `mvn test -Dtest=*CredentialsIT` (DoD del protocolo) antes de sugerir commit.

### Fase 2 — EODHD `.MX` (BMV/MXN nativo) + moneda nativa
8. [ ] Extender catálogo ([[ADR-0008]]): mapeo `symbol` → (`mercado`, sufijo EODHD `.MX`/`.US`, `emisora_serie`+`bolsa` para databursatil, moneda de cotización).
9. [ ] Resolver proveedor/símbolo por **mercado del holding** (US→`.US` USD; BMV/SIC→`.MX` MXN) e incluir mercado/moneda en la key de caché.
10. [ ] (Opcional) `DatabursatilAdapter` + `DatabursatilCredentialsIT` como fallback MXN.
11. [ ] Decisión de moneda funcional en ADR aparte (prerequisito para que el P&L en MXN use costo real).
