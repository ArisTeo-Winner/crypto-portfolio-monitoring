# ADR-0018: Valuación por clase de activo — renta fija a descuento por devengo lineal determinista

**Status:** Proposed
**Date:** 2026-10-26
**Deciders:** Aristeo (owner backend)
**Módulo:** `portfolio` (application.service.GetPortfolioTotalHistoryService, application.service.GetAssetHoldingsHistoryService, domain.engine, application.port.out), `transaction` (snapshot/proyección de campos de bono)
**Relacionado:** [[ADR-0013]] (fecha de operación vs registro-sistema), [[ADR-0014]] (corporate actions / REDEEM), [[ADR-0006]] [[ADR-0009]] [[ADR-0010]] (moneda/FX), [[ADR-0001]] (Banxico SIE), [[ADR-0012]] (calendarios)

## Context

`GET /api/v1/me/portfolio/history` (y el per-activo `/me/portfolio/assets/{symbol}/history`)
lanza **HTTP 500** cuando el portafolio incluye un `GOVERNMENT_BOND` (p.ej. `CETES91`):

```
java.lang.IllegalStateException: No market price history provider configured for GOVERNMENT_BOND
  at CachedMarketPriceHistoryAdapter.failAfterFallback(...:135)
  at CachedMarketPriceHistoryAdapter.fetchWithFallback(...:98)
  at GetPortfolioTotalHistoryService.toAssetInput(...:277)
```

Causa raíz doble:
1. **Ningún `MarketPriceHistoryProvider` soporta `GOVERNMENT_BOND`.** Cobertura actual: CRYPTO
   (Binance/Bybit/BinanceFutures/CoinGecko), STOCK/ETF/INDEX (TwelveData/Polygon/AlphaVantage).
   `fetchWithFallback` salta todos por `!supports(...)` y `failAfterFallback` lanza
   `IllegalStateException`.
2. `GetPortfolioTotalHistoryService.toAssetInput` degrada con elegancia (marca el símbolo como
   no disponible → respuesta parcial) **solo** ante `UnknownAssetSymbol | RateLimit | ServerException`;
   la `IllegalStateException` **no** se atrapa → se propaga → 500 y se cae **toda** la gráfica por
   un único bono.

El error es por **AssetType**, no por símbolo ni por fecha: cualquier bono (CETES de cualquier
plazo, UDIBONO, BONDM, e incluso los ETF de tesoro US tipados `GOVERNMENT_BOND`), con cualquier
fecha, vivo o vencido, reproduce el 500. Registrar la transacción (POST) no falla; falla solo al
pedir la gráfica que lo incluye.

### Por qué la renta fija NO cabe en el modelo de "precio de mercado por símbolo"

El contrato `MarketPriceHistoryProvider.fetchPriceHistory(assetType, symbol, range)` asume un
**precio de mercado independiente del tenedor**, uno por timestamp. Un instrumento a descuento como
CETES se valúa de forma **específica de la posición y determinista** (devengo lineal de intereses):
el valor en cualquier fecha depende de la fecha de operación, el vencimiento, el costo real y el
valor nominal — datos **conocidos todos al momento de la compra**. El símbolo `"CETES91"` es un
plazo genérico, no una emisión concreta, así que no puede producir un precio en la cadena de
proveedores; además el `PortfolioTransactionSnapshot` del motor hoy ni siquiera transporta
`maturityDate`/`faceValue`.

### El sistema es un tracker retrospectivo (as-of fecha de operación)

Las operaciones se registran **con retraso variable**: puede ser el mismo día de la compra, o
días, semanas o meses después. No es ley una ni otra; el diseño debe cubrir **todo el espectro,
lag = 0 incluido**. Por [[ADR-0013]] cada transacción lleva dos marcas de tiempo: **Evento1 =
operación** (wall-clock local + zona IANA) y **Evento2 = registro-sistema** (UTC). La valuación
debe anclarse en **Evento1 (operación)**; el momento de captura es irrelevante para el valor.

Esto **favorece** el modelo determinista: la vida completa del instrumento se reconstruye desde
los campos de la posición, sin datos externos ni curva histórica. Mark-to-market, en cambio,
exigiría la curva de Banxico en cada fecha pasada para reconstruir.

Ejemplo (calculadora cetesdirecto, CETES 28, $10,000 MXN):
```
Costo real   = 9,991.59 (CETES) + 7.15 (BONDDIA remanente) = 9,998.74
Valor final  = 10,041.56 (neto post-ISR)
Rendimiento  = +42.82 en 28 días  ->  ~1.529 / día
Valor(t)     = 9,998.74 + 1.529 * t   (recta ascendente; meseta en 'final' tras vencer)
```

Nota: ya existe `CetesMarkToMarketService` (descuento cupón cero contra la **curva vigente** de
Banxico) para su endpoint puntual `/mark-to-market`. Es una filosofía distinta (fair value que
*fluctúa*) y no se usa para la gráfica.

## Decision

Adoptar **valuación por clase de activo** en el motor de historial, con selección por `AssetType`:

- **Renta variable / market-quoted** (STOCK, ETF, INDEX, CRYPTO): ruta actual — serie de precios
  del proveedor × cantidad. Sin cambios.
- **Renta fija a descuento (cupón cero)** — `GOVERNMENT_BOND` MXN tipo CETES: **devengo lineal
  determinista**, sin datos externos.

### 1. Fórmula de devengo (as-of fecha de operación)
Para cada día `d` de la ventana:
```
term    = díasNaturales(maturityDate − T0)              (T0 = fecha de OPERACIÓN, Evento1)
elapsed = clamp(díasNaturales(d − T0), 0, term)
valor(d)= cost + (finalValue − cost) * elapsed / term
          (valor(d) = finalValue para d ≥ maturityDate)
```
- `cost` = costo real de compra (base de costo, incluye fees/remanente).
- `finalValue` = valor nominal al vencimiento (bruto vs neto-de-ISR: ver decisión abierta).
- `term > 0` siempre; `elapsed = 0` el mismo día de la operación → `valor = cost`.

### 2. Lag-agnóstico, reconstructivo, sin schedulers
- La valuación depende **solo** de los datos de la posición, **nunca** de cuándo se capturó. Dos
  usuarios con el mismo CETES (mismo T0/costo/final/vencimiento) ven la **misma curva** en fechas
  traslapadas, registren el mismo día o meses después.
- El estado `MATURED`/valor-tras-vencimiento se **deriva en tiempo de lectura** (`maturity < hoy`),
  **no** por un job programado en la fecha de vencimiento (esa fecha suele estar en el pasado).
- La "línea punteada de proyección" (hoy → vencimiento) **solo existe cuando `maturity > hoy`**;
  para un bono retrospectivo ya vencido es recta sólida + meseta, sin proyección.

### 3. Estrategia de dominio en `portfolio`, no un módulo nuevo
- Nuevo valuador de dominio (p.ej. `FixedIncomeAccrualValuator`) en `portfolio/domain/engine`,
  **pura matemática** sobre los campos de la posición. Cero dependencia a `marketdata` (el devengo
  no usa la curva). Convive con `HoldingsValueCalculator` (market-quoted) bajo una selección por
  tipo en `GetPortfolioTotalHistoryService` / `GetAssetHoldingsHistoryService`.
- **Enriquecer** el input del motor (`PortfolioTransactionSnapshot` o un input dedicado) con
  `maturityDate`, `faceValue`, `couponRate`, `currency`.

### 4. Robustez (invariante): nunca 500 por un bono
- `failAfterFallback` lanza una **excepción de dominio dedicada** (p.ej.
  `NoMarketDataProviderException extends MarketDataServerException`) para que el `catch` existente
  en `toAssetInput` ya la cubra. Un `GOVERNMENT_BOND` sin `maturityDate`/`faceValue` (dato
  incompleto) o sin valuador aplicable → se marca en `unavailableSymbols` → **respuesta parcial**,
  no 500.

### 5. Moneda
- El devengo se calcula en **MXN** y se convierte a la moneda de presentación **al final**, no por
  doble conversión (coherente con [[ADR-0006]]/[[ADR-0009]]/[[ADR-0010]] y evitando la distorsión
  de costo MXN ya conocida).

### 6. Contrato para la UI
- La serie devengada (sólida) hasta hoy, más metadatos de proyección: `maturityDate`, `termDays`,
  `daysElapsed`, `projectedFinal`. La línea punteada, el progress bar y los tooltips son
  **presentación**; el backend entrega los números, no dibuja.

## Options Considered

### Opción A: Proveedor `MarketPriceHistoryProvider` para GOVERNMENT_BOND (por símbolo)
**Pros:** encaja en la cadena existente. **Cons:** el símbolo (plazo genérico) no codifica el
vencimiento de la posición; requiere curva **histórica** de Banxico por fecha para reconstruir;
frágil y dependiente de red; no modela la realidad retail hold-to-maturity. **Rechazada.**

### Opción B: Mark-to-market (curva Banxico vigente/histórica) para la gráfica
**Pros:** reusa `CetesMarkToMarketService`. **Cons:** el valor **fluctúa** con las tasas; para
reconstruir el pasado hace falta la curva histórica en cada fecha; no refleja que el retail
mantiene a vencimiento (no hay secundario real). Se conserva **solo** para su endpoint puntual.
**Rechazada para la gráfica.**

### Opción C: Devengo lineal determinista por clase de activo (elegida)
**Pros:** determinista, reproducible, **sin red/caché/500**; cubre todo el espectro de lag de
registro de forma uniforme; retrospectivo por construcción; simplifica (elimina "tiempo real").
**Cons:** hay que enriquecer el snapshot; coexisten dos filosofías (devengo para gráfica, MTM para
su endpoint) — documentarlo; instrumentos con cupón necesitan extensión futura.

## Consequences

- **Más fácil:** un bono deja de tumbar la gráfica; la valuación de CETES es exacta y reproducible
  sin llamadas externas; el registro el mismo día o meses después da la misma curva.
- **Más difícil:** enriquecer el input del motor con campos de bono; mantener clara la distinción
  devengo (gráfica) vs MTM (endpoint puntual).
- **A revisar:** instrumentos **con cupón / indexados** (BONOS, UDIBONO, BONDES) requieren un
  devengo distinto (flujos de cupón, UDIs); penalización por retiro anticipado (secundario
  cetesdirecto); materialización del flujo de redención al vencimiento.

## Decisiones abiertas (cerrar en revisión)
1. **ISR (bruto vs neto):** ¿la recta va a valor **bruto** y el fisco se trata aparte (como
   [[ADR-0014]]), o **neto-de-ISR** como muestra cetesdirecto? Define `finalValue`.
2. **Redención al vencimiento:** (a) materializar un `REDEEM` derivado fechado en el vencimiento
   (encaja con [[ADR-0014]], útil para saldo líquido/reinversión) vs (b) solo computado (la serie ya
   refleja `valor = final` tras vencer). Recomendado: (b) ahora para la gráfica, (a) como follow-up.
3. **Alcance inicial:** solo CETES (cupón cero) en esta ADR; cupón/indexados en una posterior.

## Action Items
1. [ ] `failAfterFallback` lanza `NoMarketDataProviderException` (dominio) y `toAssetInput` la cubre
   → respuesta parcial en vez de 500 (invariante de robustez, independiente del resto).
2. [ ] Enriquecer el input del motor con `maturityDate`, `faceValue`, `couponRate`, `currency`.
3. [ ] `FixedIncomeAccrualValuator` en `portfolio/domain/engine` (devengo lineal, clamp, meseta).
4. [ ] Selección por `AssetType` en `GetPortfolioTotalHistoryService` y
   `GetAssetHoldingsHistoryService` (market-quoted → actual; GOVERNMENT_BOND cupón cero → devengo).
5. [ ] Anclar `T0` en la **fecha de operación** (Evento1, [[ADR-0013]]); guard para `T0 > hoy`.
6. [ ] Conversión MXN→moneda de presentación al final (no doble conversión).
7. [ ] Metadatos de proyección en la respuesta (`maturityDate`, `termDays`, `daysElapsed`,
   `projectedFinal`) para la UI (línea punteada + progress bar + tooltips).
8. [ ] Tests: devengo (día 0 = costo; a media vida; vencido = face; `T0` futuro), lag-agnóstico
   (misma curva registrando el mismo día vs meses después), guard de robustez (bono sin datos →
   parcial, no 500), e integración de `/me/portfolio/history` con un CETES.
9. [ ] (Follow-up) Decidir/implementar redención materializada y extensión a cupón/indexados.
