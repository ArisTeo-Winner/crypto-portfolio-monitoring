# ADR-0022: Símbolo canónico en la cadena de precios — Kraken vía `assetVersion=1` (sin tabla de alias)

**Status:** Accepted
**Date:** 2026-10-31
**Deciders:** Aristeo (owner)
**Módulo:** `portfolio` (cadena de historia de precios en `portfolio/infrastructure/outbound/marketdata/`); consume identidad canónica de `asset`
**Relacionado:** [[ADR-0021]] (Coinbase/Kraken primarios, Binance/Bybit gateados), [[ADR-0008]] (catálogo = fuente única de metadata de activos), [[ADR-0019]] (identidad canónica vía OpenFIGI), [[reference_portfolio_chart_routing]].

## Context

Al añadir Kraken como proveedor de velas ([[ADR-0021]] F2) aparece un choque de vocabularios. Kraken tiene dos formas internas para los activos "originales":

- Esquema **X/Z** estilo ISO 4217: `X` = cripto (`XXBT`, `XXDG`), `Z` = fiat (`ZUSD`). Bitcoin contra dólar = `XXBTZUSD`.
- **altname**: forma corta sin prefijo del par (`XBTUSD`), donde Bitcoin es `XBT` (ISO reserva la `B`) y Dogecoin es `XDG`.

Nuestro sistema, en cambio, tiene **un** símbolo canónico: `asset_catalog.symbol` normalizado a `trim + UPPER` (`BTC`, `DOGE`, `SOL`), con nombre humano en `asset_catalog.name` ([[ADR-0008]]). Transacciones, portafolio, API y UI operan siempre sobre ese canónico; el usuario ve "Bitcoin", nunca un ticker de exchange.

La duda de diseño original: ¿dónde vive el mapeo `BTC→XBT` y cómo se evita que `XXBT`/`XXBTZUSD` se filtren al usuario?

### Hallazgo empírico (probado en vivo contra la API de Kraken, 2026-10-31)

| Petición | `error` | Llave en `result` |
|---|---|---|
| `/AssetPairs?pair=BTCUSD` (legacy) | `EQuery:Unknown asset pair` | — |
| `/AssetPairs?pair=XBTUSD` (legacy) | `[]` | `XXBTZUSD` (base `XXBT`, quote `ZUSD`, wsname `XBT/USD`) |
| `/OHLC?pair=BTCUSD&assetVersion=1` | `[]` | `BTC/USD` |
| `/OHLC?pair=DOGEUSD&assetVersion=1` | `[]` | `DOGE/USD` |
| `/OHLC?pair=SOLUSD&assetVersion=1` | `[]` | `SOL/USD` |

El parámetro `assetVersion=1` (documentado; "Only `assetVersion=1` is currently supported") hace que Kraken use **display names** tanto en el input aceptado como en las llaves de respuesta. Y el display name de Kraken **coincide exactamente con nuestro canónico** (`BTC`, `DOGE`, `SOL`). En modo legacy (default) hay que traducir a `XBT` y leer `XXBTZUSD`; con `assetVersion=1` no.

## Decision

Usar `assetVersion=1` en la integración de Kraken. Con eso, Kraken habla nuestro vocabulario canónico en ambos lados y **no se necesita ninguna tabla de alias ni traducción de símbolo**.

1. **Símbolo canónico = fuente única.** `asset_catalog.symbol` (`BTC`, `trim + UPPER`) es la identidad para almacenamiento, ruteo, API y UI; el display usa `asset_catalog.name` ([[ADR-0008]]). Ningún símbolo interno de Kraken (`XBT`, `XXBT`, `XXBTZUSD`) se persiste ni sale por la API.

2. **El `KrakenMarketPriceHistoryAdapter` pide en display:**
   - `pair = {canónico}USD` (p. ej. `BTC` → `BTCUSD`), parámetro `assetVersion=1`, `interval` en minutos (1/5/15/30/60/240/1440/10080/21600), paginación por cursor `last`.
   - Lee la **única entrada de `result` cuyo key `!= "last"`** (será `BTC/USD`); no reconstruye la llave ni interpreta el esquema X/Z.
   - Vela Kraken = `[time, open, high, low, close, vwap, volume, count]` → cierre en **índice 4**, `time` en **índice 0 en segundos**.

3. **Sin tabla de alias, sin `ExchangeSymbolMapper`, sin constante, sin config.** No existe `BTC→XBT` en el código: el seed propuesto originalmente queda **eliminado** porque `assetVersion=1` lo vuelve innecesario. `XBT`/`XXBT`/`XXBTZUSD` no aparecen en ninguna parte de nuestro código.

4. **Cobertura y degradación.** Si algún día el display name de un activo en Kraken difiere de nuestro canónico, su par simplemente no resolverá → `UnknownAssetSymbolException` → la cadena cae a CoinGecko ([[ADR-0021]]). Solo en ese caso puntual se introduciría una traducción mínima, y aun así confinada al adapter.

5. **El contrato se valida en vivo, no en runtime.** El `KrakenCredentialsIT` ([[ADR-0021]], protocolo `*CredentialsIT`) golpea `/OHLC?pair=BTCUSD&assetVersion=1` y asegura `error: []` + llave `BTC/USD` + cierre positivo. Si Kraken cambiara el comportamiento de `assetVersion=1`, el test truena antes del deploy.

6. **Coinbase ([[ADR-0021]] F1) no cambia:** ya pide en canónico `{símbolo}-USD` sin alias. El principio es el mismo en ambos proveedores: la forma del par se arma en el adapter a partir del canónico; nada de vocabulario de exchange río arriba.

## Options Considered

### Opción A: `assetVersion=1` — Kraken en display, cero mapeo ✅ (elegida)
| Dimensión | Evaluación |
|---|---|
| Complejidad | Mínima — un query param; sin tabla, sin helper, sin config |
| Costo | Cero en runtime; sin estructura que mantener |
| Escalabilidad | Máxima — activos nuevos funcionan solos mientras su display name = canónico |
| Riesgo | Depende de `assetVersion=1` (parámetro relativamente nuevo); mitigado por `KrakenCredentialsIT` |

**Pros:** no hay `XBT` en el código ni en ningún lado; el input y la respuesta ya vienen en nuestro vocabulario; nada que hardcodear ni versionar.
**Cons:** acopla a una feature específica de Kraken (`assetVersion=1`); si Kraken la deprecara habría que volver a legacy + traducción (lo detecta el IT).

### Opción B: altname legacy + seed hardcodeado (`BTC→XBT`, `DOGE→XDG`) + lectura de llave dinámica
| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja, pero introduce una constante de alias y el conocimiento del esquema X/Z |
| Escalabilidad | Media — cada divergencia nueva exige una entrada |

**Pros:** no depende de `assetVersion=1`; funciona en el modo default/estable.
**Cons:** reintroduce "código duro" (`XBT`) que `assetVersion=1` vuelve innecesario; hay que mantener el seed. Era el plan previo a la prueba en vivo; se descarta al confirmar que `assetVersion=1` acepta `BTCUSD` y devuelve `BTC/USD`.

### Opción C: bajar el diccionario de Kraken (`/Assets`, `/AssetPairs`) en runtime y construir el mapa
| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta — fetch al arranque, caché, parseo, manejo de fallos |
| Escalabilidad | Aparente |

**Cons:** sobre-ingeniería; además el diccionario de Kraken está en **su** vocabulario (`XXBT`) y nunca contiene el token `"BTC"`, así que no puede, por sí solo, puentear nuestro canónico. No resuelve el problema que pretende.

### (Descartadas) Alternativas que violan el borde
- Guardar `XBT` o un campo `providerSymbol` en `asset_catalog`, o exponer el símbolo de proveedor en cualquier DTO de respuesta → filtra vocabulario de exchange al dominio y a la UI.

## Trade-off Analysis

La prueba en vivo reordenó las opciones. La duda era *dónde* guardar `BTC→XBT`; la respuesta resultó ser *no guardarlo, porque Kraken ya ofrece hablar en display*. `assetVersion=1` reduce la integración a "arma `{canónico}USD`, manda el flag, lee la llave que venga" — sin tabla, sin constante, sin el esquema X/Z modelado en ningún lado. El único costo es el acoplamiento a esa feature de Kraken, y es barato de cubrir: un `CredentialsIT` que falla ruidosamente si el contrato cambia, y el fallback a CoinGecko que ya existe. La Opción B (hardcode) solo tendría sentido si `assetVersion=1` desapareciera; la Opción C nunca resolvía el puente. La exposición al usuario sigue garantizada por el catálogo ([[ADR-0008]]): el canónico entra y sale sin tocar vocabulario de Kraken.

## Consequences

**Más fácil:**
- El usuario ve "Bitcoin"/`BTC`, jamás `XXBT`; y ahora tampoco hay traducción interna que mantener.
- Añadir un activo cripto nuevo = funciona solo en Kraken mientras su display name = canónico; sin tocar código ni config.
- La integración de Kraken es casi idéntica a la de Coinbase (arma par desde canónico + lee velas), solo que con `assetVersion=1` e `interval` en minutos y cierre en índice 4.

**Más difícil / a vigilar:**
- Dependencia de `assetVersion=1`: documentado pero relativamente nuevo. El `KrakenCredentialsIT` es la barrera que lo detecta si cambia.
- Divergencias de display name (activo cuyo ticker en Kraken ≠ canónico): hoy no observadas en el universo relevante (BTC, DOGE, SOL, XRP coinciden); si aparece, el par falla y cae a CoinGecko.
- Orden de la vela distinto de Coinbase (`O,H,L,C` vs `L,H,O,C`); ambos con cierre en índice 4, pero documentarlo para no confundir.

**A revisitar:**
- Si Kraken deprecara `assetVersion=1`, volver a Opción B (altname + seed mínimo). El cambio quedaría contenido en el adapter.
- Observabilidad ([[ADR-0021]] F3): registrar qué proveedor sirvió cada símbolo canónico.

## Action Items

### Fase 2 — Kraken secundario (con `assetVersion=1`) — HECHA
1. [x] `KrakenWebClientConfig` (`base-url` `https://api.kraken.com`, timeout por `@Value`).
2. [x] `KrakenMarketPriceHistoryAdapter` (`@Order(-10)`, entre Coinbase `-20` y CoinGecko `10`): `GET /0/public/OHLC?pair={canónico}USD&assetVersion=1&interval={min}&since={cursor}`; lectura de la llave `!= "last"`; vela `O,H,L,C` cierre índice 4, time índice 0 en segundos; paginación por `last`; `error[]` no vacío → `UnknownAssetSymbolException`/`MarketDataRateLimitException`/`MarketDataServerException`; flag `external.providers.kraken.enabled`.
3. [x] **Sin** tabla de alias, **sin** `ExchangeSymbolMapper`, **sin** constante `BTC→XBT`.
4. [x] Tests unit (MockWebServer) + `KrakenCredentialsIT` que valida en vivo `pair=BTCUSD&assetVersion=1` → `error: []`, llave `BTC/USD`, cierre positivo. **Verde** (unit 3 casos + CredentialsIT en vivo, 2026-11-02).
5. [x] Gateo `external.providers.kraken.enabled=false` en `AssetHoldingsHistoryFlowIT` y `PortfolioTotalHistoryIntegrationIT` (evita que Kraken `@Order(-10)` golpee red real al apagar Coinbase).
