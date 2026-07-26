# ADR-0002: Cobertura `*CredentialsIT` para todos los proveedores de market data

**Status:** Proposed
**Date:** 2026-07-25
**Deciders:** Aristeo (owner backend)
**Módulo:** `marketdata` (tests de integración en `com.mx.cryptomonitor.integration.marketdata`)

## Context

El protocolo de `CLAUDE.md` ("PROTOCOLO OBLIGATORIO DE INTEGRACIÓN DE APIS") exige que **todo
proveedor externo** tenga una clase `[Proveedor]CredentialsIT` que haga una llamada real a un
endpoint básico/health para validar en vivo: token vigente/caducado, licencia activa y consistencia
del contrato JSON. DoD: `mvn test -Dtest=*CredentialsIT`.

Auditoría del estado actual:

| Proveedor      | Adapter                         | `CredentialsIT` |
| -------------- | ------------------------------- | --------------- |
| Banxico SIE    | `BanxicoRateAdapter`            | ✅ existe        |
| DataBursatil   | `DataBursatilAdapter`           | ✅ existe        |
| Finnhub        | `FinnhubProfileAdapter`         | ✅ (en `asset`)  |
| CoinMarketCap  | `CoinMarketCapAdapter`          | ❌ **falta**     |
| CoinGecko      | `CoinGecko*Adapter`             | ❌ **falta**     |
| AlphaVantage   | `AlphaVantageAdapter`           | ❌ **falta**     |
| Massive        | `MassiveAdapter`                | ❌ **falta**     |
| TwelveData     | `TwelveDataAdapter`             | ❌ **falta**     |
| Polygon        | `PolygonMarketPriceHistoryAdapter` | ❌ **falta**  |
| Binance        | `BinanceMarketPriceHistoryAdapter` | ⚠️ N/A       |

## Decision

Generar **6** clases `*CredentialsIT` (CoinMarketCap, CoinGecko, AlphaVantage, Massive, TwelveData,
Polygon) en `com.mx.cryptomonitor.integration.marketdata`, siguiendo el patrón de `FinnhubCredentialsIT`:
**llamada HTTP cruda** (no reusar el adapter) a un endpoint barato que valide la credencial, con:

1. `Assumptions.assumeTrue(hasText(apiKey))` → se **omite** (no falla) si no hay credencial en el
   entorno; el sufijo `IT` mantiene el test fuera de `mvn test`.
2. `fail(...)` explícito ante HTTP 401/403 (o envelope de error en body 200) → distingue token
   caducado/revocado.
3. Assert sobre un **campo del contrato** (mapping check) → detecta cambios de contrato de la API.

**Binance queda excluido**: su endpoint de klines es público (sin token). No hay credencial que
expire, así que un `CredentialsIT` es semánticamente incorrecto. Se documenta como acción aparte un
`BinanceContractIT` opcional (solo mapping check).

## Options Considered

### Opción A: Reusar cada adapter (como Banxico/DataBursatil)

| Dimensión           | Evaluación |
| ------------------- | ---------- |
| Complejidad         | Baja       |
| Fidelidad de la señal | **Mala** |
| Familiaridad equipo | Alta       |

**Pros:** menos código; prueba el mismo camino que producción.
**Cons:** `MassiveAdapter`, `AlphaVantageAdapter`, `TwelveDataAdapter`, `PolygonAdapter` **tragan el
error** (devuelven `Optional.empty()` / lista vacía) o clasifican a distintas excepciones. A través
del adapter, un token caducado es **indistinguible** de "sin datos". Justo el caso que este test debe
atrapar. Rechazada — es la misma razón por la que `FinnhubCredentialsIT` NO reusa su adapter.

### Opción B: Llamada HTTP cruda por proveedor (patrón Finnhub) ✅

| Dimensión           | Evaluación |
| ------------------- | ---------- |
| Complejidad         | Baja-media |
| Fidelidad de la señal | **Alta** |
| Familiaridad equipo | Alta       |

Cada IT arma un `WebClient` con la env var real y golpea el endpoint de validación de credencial,
inspeccionando el status HTTP crudo y el envelope de error en body. Detecta 401/403, el
`{"Information": ...}` de AlphaVantage y el `{"status":"error","code":401}` de TwelveData.

**Pros:** señal inequívoca (token caducado vs. contrato cambiado vs. red); independiente de la
resiliencia del adapter; self-contained.
**Cons:** duplica ~el mismo esqueleto 6 veces (mitigable con base class, ver consecuencias).

### Opción C: Base class `AbstractCredentialsIT` compartida

**Pros:** DRY; un solo lugar para el skip/timeout/aserción genérica.
**Cons:** las 3 ITs existentes son standalone; introducir la base tocaría 3 archivos y cada proveedor
tiene auth (header vs query) y envelope de error distinto, así que la parte compartible es pequeña.
Diferida — se puede extraer después sin romper nada.

## Trade-off Analysis

El eje es **fidelidad de la señal de fallo**, no ahorro de líneas. La Opción A comparte código pero
ciega el test ante el escenario que importa (token caducado silenciado por el adapter resiliente). La
Opción B replica el patrón ya probado en `FinnhubCredentialsIT`, que existe precisamente porque su
adapter traga el error. Se elige **B**; **C** se deja como refactor futuro cuando haya ≥6 ITs y el
patrón esté estable.

## Matriz de validación (endpoint + auth + contrato)

| Proveedor     | Endpoint validador                         | Auth                       | Campo asertado |
| ------------- | ------------------------------------------ | -------------------------- | -------------- |
| CoinMarketCap | `/v1/key/info`                             | header `X-CMC_PRO_API_KEY` | `data`         |
| CoinGecko     | `/ping`                                    | header `x-cg-pro-api-key`  | `gecko_says`   |
| AlphaVantage  | `/query?function=GLOBAL_QUOTE&symbol=IBM`  | query `apikey`             | `Global Quote` |
| Massive       | `/v2/aggs/ticker/AAPL/prev`                | query `apiKey`             | `status`=OK    |
| TwelveData    | `/price?symbol=AAPL`                        | query `apikey`             | `price`        |
| Polygon       | `/v1/marketstatus/now`                     | query `apiKey`             | `market`       |

`/v1/key/info` (CMC) y `/v1/marketstatus/now` (Polygon) no consumen créditos de cotización — son los
más baratos para validar la llave.

## Consequences

**Más fácil:** un `mvn verify -Dtest=*CredentialsIT` en el pipeline detecta token caducado o cambio
de contrato de cualquier proveedor **antes** de que rompa producción.

**Más difícil / a vigilar:**
- **CoinGecko**: la key es opcional (free tier). El IT se omite sin key; su config usa el header
  `x-cg-pro-api-key` con el host público — hay un desajuste latente demo/pro a revisar.
- **AlphaVantage/TwelveData**: devuelven error en body con HTTP 200; el assert de contrato debe
  reconocer el envelope de error, no solo el 401.
- **Massive/Polygon/AlphaVantage** no tienen base URL por defecto en `application.properties`; el IT
  aporta un default público, salvo Massive (se omite si falta `API_MASSIVE_BASE_URL`).
- **Refactor a base class** (Opción C) cuando el patrón se estabilice.

## Action Items

1. [x] Generar `CoinMarketCapCredentialsIT`, `CoinGeckoCredentialsIT`, `AlphaVantageCredentialsIT`,
       `MassiveCredentialsIT`, `TwelveDataCredentialsIT`, `PolygonCredentialsIT`.
2. [x] Compilar y ejecutar `mvn -Dtest=*CredentialsIT test` en local: las 9 clases se detectan y se
       omiten limpio sin env vars (`assumeTrue`). El árbol de tests compila (el fallo previo era
       estado de build stale, no clases faltantes).
3. [x] Añadir el step `-Dtest=*CredentialsIT` al Jenkinsfile como gate de credenciales/contrato
       (etapa `Credential & Contract Validation`, gateada a `dev`/`main`).
4. [ ] Ejecutar el gate en `dev`/`main` con las credenciales reales inyectadas para confirmar que
       ningún token está caducado (validación en vivo, no cubierta por el skip local).
5. [ ] (Opcional) `BinanceContractIT` — mapping check sin credencial.
6. [ ] (Diferido) Extraer `AbstractCredentialsIT` cuando el patrón esté estable.
