# ADR-0021: Proveedores de precio de cripto bajo geobloqueo US — Coinbase/Kraken primarios desde host US, Binance/Bybit gateados por región

**Status:** Accepted
**Date:** 2026-10-30
**Deciders:** Aristeo (owner)
**Módulo:** `portfolio` (cadena de historia de precios en `portfolio/infrastructure/outbound/marketdata/`); consume `marketdata` (CoinGecko) vía fallback existente
**Relacionado:** [[ADR-0002]] (CredentialsIT como health-check en vivo), [[ADR-0016]] (proveedores de precio de hoy en acciones), [[reference_chart_range_and_provider_fallback]] (tokens 1D..ALL + fallback Binance/CoinGecko), [[reference_portfolio_chart_routing]].

## Context

El backend corre en **Render, región Oregon (US West)** → IP de salida estadounidense. **Binance** (`api.binance.com`) y **Bybit** responden **HTTP 451 (Unavailable For Legal Reasons)** a toda petición desde IPs de EE. UU. por cumplimiento legal/geográfico. El bloqueo **no es del producto Binance/Bybit sino de la IP de salida**: desde México (dev local) funciona, por eso no se había detectado. Confirmado además que el mirror "solo datos" `data-api.binance.vision` **no** evade el bloqueo US de forma confiable (reportes de la comunidad siguen viendo 451). Las IPs de nube además arriesgan baneo automático (HTTP 418) por volumen.

La cadena de historia de precios de cripto vive en `portfolio/infrastructure/outbound/marketdata/`:
- Interfaz `MarketPriceHistoryProvider` (`supports(AssetType)`, `fetchPriceHistory(... HoldingsHistoryRange|ChartResolution)`), implementaciones como beans `@Component @Order(n)` inyectadas en orden a `CachedMarketPriceHistoryAdapter`.
- Orden actual (crypto): `BinanceMarketPriceHistoryAdapter` `@Order(0)` → `BinanceFutures` → `Bybit` → `TwelveData` → CoinGecko (fallback).
- Cada proveedor ya tiene flag `external.providers.<x>.enabled` (default `true`); al estar deshabilitado lanza una excepción recuperable y el orquestador **cae al siguiente** proveedor.
- CoinGecko ([[ADR-0016]] no aplica; es `CryptoHistoricalPricePort`) es el fallback pero el plan es **muy limitado** (rate/ventanas).

Resultado en prod: todo gráfico de cripto depende de CoinGecko (frágil) porque los primarios devuelven 451.

## Decision

La causa raíz es **IP de salida (US) ↔ exchange que bloquea US**. Un backend US (producto además MX-céntrico) **no debe depender de exchanges bloqueados-en-US**; es frágil (los geobloqueos se mueven) y huele a compliance. Se decide **conservar el host US y cambiar la fuente primaria de cripto a exchanges US-OK**, no reubicar el host.

1. **Fuente primaria de OHLC = exchanges US-OK, sin API key**, en cadena de fallback:
   **Coinbase** (primario; historia profunda vía paginación `start/end`, granularidades 1m→1d) → **Kraken** (secundario; simple, tope ~720 velas/llamada) → **CoinGecko** (fallback de cobertura amplia por `id`). Twelve Data y Polygon (`massive`), ya cableados y US-OK, quedan como alternos pagados si hiciera falta profundidad/cobertura.

2. **Binance/Bybit/BinanceFutures NO se eliminan: se gatean** por `external.providers.<x>.enabled`.
   - **Prod (Render US):** `false` → quedan fuera de la cadena (el orquestador ya cae al siguiente sin tocar código).
   - **Dev local (MX):** `true` → siguen funcionando, conservando paridad y el histórico del patrón.
   - Se recuperan tal cual si algún día el host se mueve a EU.

3. **Sin migración de host.** Mover la región de Render implica **crear un servicio nuevo** (la región se fija al crear y no se cambia), re-apuntar DB (Supabase) y Redis, e introducir **latencia cross-región en todo el plano de datos**, no solo cripto. Se descarta.

4. **Se conserva la arquitectura** `MarketPriceHistoryProvider` + `@Order` + `CachedMarketPriceHistoryAdapter`. Los nuevos adapters son **espejo** del de Binance (paginación, mapeo de `ChartResolution`, excepciones `UnknownAssetSymbolException`/`MarketDataRateLimitException`/`MarketDataServerException`, `PricePoint(time, close)`), con su propio mapeo de símbolo (`BTC-USD` Coinbase, `XBTUSD`/`BTCUSD` Kraken) y de intervalo (Coinbase en **segundos**, Kraken en **minutos**).

## Options Considered

| Opción | Pro | Con |
|---|---|---|
| **B. Conservar host US, cambiar proveedor** ✅ | Mata el 451 de raíz; sin migrar host ni latencia; compliance-limpio (host US → fuentes US-OK); reutiliza la cadena hexagonal | Cobertura long-tail recae en CoinGecko; topes por request ⇒ paginar; mapeo de símbolo por exchange |
| A. Mover Render a Frankfurt/Singapore, conservar Binance/Bybit | Conserva adapters y cobertura amplia de Binance | Región fija al crear ⇒ servicio nuevo + re-apuntar DB/Redis; latencia cross-región en todo; sigue siendo workaround contra un muro que se mueve |
| C. Apuntar Binance a `data-api.binance.vision` | Cambio de una línea | No evade el bloqueo US de forma confiable (451 igual); baja confianza. Solo sirve como prueba de 5 min |
| Cambiar Binance por Bybit | — | **No arregla nada**: Bybit bloquea US igual |

## Trade-off Analysis

Coinbase + Kraken son exchanges regulados en EE. UU.: dan velas de calidad, sin key, desde una IP US, que es justo donde vive el backend. El costo es cobertura (no listan todo lo que lista Binance) y topes por request que obligan a paginar — ambos ya resueltos por el patrón existente (paginación en el adapter de Binance, fallback a CoinGecko por cobertura). La alternativa de mover el host paga ese costo en **latencia permanente de todo el plano de datos** y en una pelea recurrente contra geobloqueos, a cambio de conservar un proveedor que de todos modos es un riesgo de compliance para un host US. Gatear (no borrar) Binance/Bybit conserva la paridad de dev local MX a costo cero.

## Consequences

**Más fácil:**
- Gráficos de cripto dejan de depender del plan limitado de CoinGecko; dos fuentes US-OK de velas por delante.
- Sin migración de host ni latencia cross-región; dev local MX intacto.

**Más difícil / a vigilar:**
- **Cobertura**: símbolos long-tail no listados en Coinbase/Kraken caen a CoinGecko (por `id`); vigilar los símbolos registrados que no resuelvan.
- **Mapeo**: símbolo (`BTC-USD`/`XBTUSD`) e intervalo (segundos/minutos) por exchange; profundidad de historia por topes/paginación.
- **Gateo por entorno**: `external.providers.binance.enabled` / `...bybit.enabled` deben quedar `false` en Render y `true` en dev; documentar en el `.env`/variables de Render.

**A revisitar:**
- Si la cobertura de Coinbase/Kraken resulta corta para los activos registrados, promover Twelve Data o Polygon (`massive`, ya pagado y validado con OHLC de cripto) como primario.

## Action Items

### Fase 1 — Coinbase primario + gateo de Binance/Bybit (núcleo) — HECHA
1. [x] `CoinbaseMarketPriceHistoryAdapter` (`@Order(-20)`) + `CoinbaseWebClientConfig`. (Migrado a Advanced Trade/CDP con JWT ES256, ver [[ADR-0023]]: `GET /api/v3/brokerage/products/{BASE-USD}/candles`, granularidad enum, 350 velas/req.)
2. [x] Gatear `external.providers.binance.enabled`, `...binance.futures.enabled`, `...bybit.enabled` a `false` en prod (variables de Render) y `true` en dev local.
3. [x] Tests unit (MockWebServer) del adapter + `CoinbaseCredentialsIT` **health-check en vivo** (se omite sin credencial CDP).
4. [ ] Verificar en Render (US) que un gráfico de cripto responde por Coinbase, no por CoinGecko. *(pendiente de deploy + provisión de llave CDP)*

### Fase 2 — Kraken secundario — HECHA
5. [x] `KrakenMarketPriceHistoryAdapter` (`@Order(-10)`) + `KrakenWebClientConfig`: `GET /0/public/OHLC?pair={canónico}USD&assetVersion=1&interval={min}&since=`, intervalo en minutos, `error[]`→excepciones recuperables, flag `external.providers.kraken.enabled`. Tests unit + `KrakenCredentialsIT` en vivo (verdes). Sin tabla de alias ([[ADR-0022]]).

### Fase 3 — Cobertura y observabilidad
6. [x] Métrica `marketdata.pricehistory.fetch{provider,asset_type,outcome}` (Micrometer→`/actuator/prometheus`) + log INFO "qué proveedor sirvió cada símbolo", instrumentados en `CachedMarketPriceHistoryAdapter.fetchWithFallback`. La alerta de credencial Coinbase (401/403, [[ADR-0023]]) se cubre con `provider=Coinbase…, outcome=server_error`. Tags de baja cardinalidad: `symbol` va al log, no a la métrica.
7. [ ] Promoción opcional a Twelve Data / Polygon (`massive`) si la cobertura de exchanges US-OK es insuficiente para el universo registrado.
8. [ ] *(futuro)* Métrica/alerta de símbolos registrados que solo resuelven por CoinGecko (o por ninguno) — requiere correlacionar el catálogo con el `provider` servido.
