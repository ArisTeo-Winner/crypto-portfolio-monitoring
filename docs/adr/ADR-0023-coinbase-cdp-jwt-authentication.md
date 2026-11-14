# ADR-0023: Autenticación de Coinbase vía JWT ES256 efímero (Advanced Trade / CDP)

**Status:** Accepted
**Date:** 2026-11-02
**Deciders:** Aristeo (owner)
**Módulo:** `portfolio` (adapter de velas de cripto en `portfolio/infrastructure/outbound/marketdata/`)
**Relacionado:** [[ADR-0021]] (Coinbase primario US-OK), [[ADR-0022]] (símbolo canónico en la cadena), protocolo `*CredentialsIT` del CLAUDE.md.

## Context

En [[ADR-0021]] F1 el adapter de Coinbase se construyó contra el endpoint **público keyless** de Coinbase Exchange (`api.exchange.coinbase.com/products/{id}/candles`). Se verificó en vivo que tanto ese endpoint como el público de Advanced Trade (`/api/v3/brokerage/market/products/{id}/candles`) devuelven velas **sin autenticación**.

El owner decidió usar la API **autenticada** de Coinbase Advanced Trade (CDP): `GET /api/v3/brokerage/products/{id}/candles`. Motivos: mayor rate limit y soporte oficial de largo plazo (la API Exchange es la más antigua; los endpoints keyless tienen límites más bajos y riesgo de deprecación).

La API CDP exige que **cada petición REST** lleve un **JWT firmado con ES256** usando la llave privada de la cuenta. Dos restricciones de diseño de Coinbase:
1. **Vida muy corta (120 s):** token efímero para mitigar intercepción.
2. **Ligado a método + ruta:** el claim `uri` = `"{MÉTODO} {host}{path}"`; un token firmado para `/candles` no sirve para otra ruta ni para otro método. El query string no entra en `uri`.

## Decision

1. **Firma por petición, sin almacenar el token.** Dada la vida de 120 s y el ligado a ruta, se firma en el momento, justo antes de la llamada (ES256 es microsegundos). Sin token store, sin refresh, sin lifecycle.

2. **Componente `CoinbaseJwtSigner`** en `portfolio/infrastructure/outbound/marketdata/` (NO un módulo nuevo de Modulith; es infraestructura del adapter). Firma con **JJWT** (ya en el stack) + **BouncyCastle** (`bcpkix-jdk18on`) para parsear el PEM **SEC1** (`EC PRIVATE KEY`) que la JVM no lee de fábrica. Header `{alg:ES256, kid:<key-name>, nonce, typ:JWT}`; claims `{iss:"cdp", sub:<key-name>, nbf:now, exp:now+120s, uri}`.

3. **Credenciales por configuración, nunca hardcodeadas ni en logs** (protocolo CLAUDE.md): `external.providers.coinbase.cdp.key-name` (id, `organizations/.../apiKeys/...`) y `...cdp.private-key` (PEM), ambos `${ENV}` vacíos por defecto. El PEM admite saltos reales o escapados (`\n`). La llave se parsea una sola vez al construir; los mensajes de error son genéricos (no exponen material).

4. **Degradación si falta la credencial.** `CoinbaseJwtSigner.isConfigured()` = false cuando no hay llave/id. El adapter entonces lanza `UnknownAssetSymbolException` (recuperable) y la cadena cae al siguiente proveedor ([[ADR-0021]]: Kraken→CoinGecko). Así, sin credencial CDP, el sistema sigue graficando por los otros proveedores.

5. **Mapeo de errores HTTP** (todos recuperables → fallback): `400/404` → `UnknownAssetSymbolException`; `401/403` → `MarketDataServerException` **con `log.warn`** ("check CDP key"); `429` → `MarketDataRateLimitException`; `5xx` → `MarketDataServerException`. Un `401` no se traga en silencio: se registra para operación, pero no tira la gráfica (cae a fallback) y lo detecta el `CredentialsIT`.

6. **Contrato de la respuesta.** `{"candles":[{start,low,high,open,close,volume}]}` con `start` en segundos; se mapea `start`→`Instant` y `close`→`BigDecimal`. Máx 350 velas por petición → paginación por ventanas de `350 * granularidad`. Granularidad enum de Advanced Trade: `ONE_MINUTE/FIVE_MINUTE/FIFTEEN_MINUTE/THIRTY_MINUTE/ONE_HOUR/TWO_HOUR/SIX_HOUR/ONE_DAY`.

7. **Validación en vivo.** `CoinbaseCredentialsIT` ejercita el `CoinbaseJwtSigner` real (parseo PEM + firma) contra `/api/v3/brokerage/products/BTC-USD/candles` y confirma token vigente + licencia activa + forma del JSON. Se **omite** (no falla) si no hay credencial en el entorno. El `CoinbaseJwtSignerTest` cubre la firma/verificación ES256 y el parseo de PEM sin red (para CI).

## Options Considered

| Opción | Pro | Con |
|---|---|---|
| **A. CDP autenticado (JWT ES256)** ✅ | Mayor rate limit; API moderna con soporte oficial | Firma ES256 + BouncyCastle + custodia de llave privada; token por petición |
| B. CDP keyless (`/brokerage/market/...`) | Plataforma moderna sin firmar nada | Rate limits más bajos; sin ventaja de cuenta autenticada |
| C. Exchange keyless (F1 original) | Cero cambios, ya verde | API más antigua de Coinbase; límites bajos; riesgo de deprecación |

Se eligió A por decisión del owner (rate limit + longevidad). B y C quedan como fallback trivial si se quisiera revertir (cambiar base-url/ruta y quitar el header).

## Consequences

**Más fácil:**
- Mayor cuota de peticiones y soporte oficial de Coinbase.
- Firma encapsulada en un componente con una sola responsabilidad; secretos fuera del código.

**Más difícil / a vigilar:**
- Nueva dependencia (BouncyCastle) y un secreto que custodiar (llave privada EC). En Render: setear `COINBASE_CDP_KEY_NAME` y `COINBASE_CDP_PRIVATE_KEY`.
- Sin credencial, Coinbase no sirve (cae a Kraken/CoinGecko); hay que provisionar la llave para que el primario funcione.
- Solo se soporta llave **ECDSA (ES256, PEM SEC1)**. Si Coinbase emite llaves **Ed25519** (base64), habría que añadir esa rama (EdDSA).

**A revisitar:**
- Caché del token por `(método, ruta)` dentro de los 120 s si el volumen creciera (hoy se firma por petición; es barato).
- Observabilidad ([[ADR-0021]] F3): métrica de 401/403 de Coinbase como alerta de credencial.

## Action Items
1. [x] `bcpkix-jdk18on` en `pom.xml`.
2. [x] `CoinbaseJwtSigner` (ES256, secretos por `@Value`, parseo PEM una vez, nunca en logs).
3. [x] Adapter a `/api/v3/brokerage/products/{id}/candles` con `Authorization: Bearer` por petición; schema `{candles:[...]}`; granularidad enum; 401/403→warn+fallback.
4. [x] `external.providers.coinbase.cdp.*` en `application.properties` (base-url a `api.coinbase.com`).
5. [x] `CoinbaseJwtSignerTest` + `CoinbaseMarketPriceHistoryAdapterTest` (MockWebServer) + `CoinbaseCredentialsIT` (en vivo, omitido sin credencial).
6. [ ] Setear `COINBASE_CDP_KEY_NAME` / `COINBASE_CDP_PRIVATE_KEY` en Render y correr `mvn test -Dtest=*CredentialsIT` como DoD.
