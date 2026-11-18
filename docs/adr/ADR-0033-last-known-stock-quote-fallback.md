# ADR-0033: Último precio conocido y retención del cierre oficial con mercado cerrado

**Status:** Accepted — Fase 1 implementada el 2026-11-16 en `feature/portfolio-historical-engine` (sin commit)
**Date:** 2026-11-15
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (orquestador `StockQuoteOrchestrator`, calendario `TradingCalendarService`, nuevos puertos + adapters Redis y TwelveData `market_state` / `eod`). Sin cambios en `portfolio` ni en `shared` en la Fase 1.
**Relacionado:** [[ADR-0012]] (calendarios de mercado NYSE/BMV), [[ADR-0016]] (proveedores de precio de hoy, TTL `PT15M`), [[ADR-0021]] (cadena de proveedores con fallback).

## Context

`StockQuoteOrchestrator.getLatest(symbol)` es la única puerta al precio actual de STOCK/ETF. Lleva `@Cacheable("stockPrices")` con TTL de 15 minutos y recorre la cadena EODHD → TwelveData → AlphaVantage → Massive.

**Problemas comprobados (15.11.2026, logs de Render y Redis local):**

1. **No distingue mercado abierto de cerrado.** `TradingCalendarService` ([[ADR-0012]]) solo alimenta `/market/status`. El refresco de `PriceUpdateService` corre cada 5 minutos todos los días, así que cada símbolo provoca una consulta real a proveedores cada ~15 minutos, también de noche, en fin de semana y en festivos, para obtener un cierre que no puede cambiar. Son hasta 96 llenados diarios por símbolo, de los que solo ~26 caen en horario de sesión.
2. **No existe un último precio conocido.** Al vencer el TTL, Redis borra la llave. Si en ese momento fallan todos los proveedores, `getLatest` lanza la excepción del primer fallo y no queda ningún valor que devolver.
3. **Un fallo no se cachea, así que se reintenta sin freno.** `PriceUpdateService` pide el precio una vez por *entry*. CRCL tiene 4 entries: cuando la cadena falla, se recorre completa 4 veces en menos de un segundo, cada 5 minutos. TwelveData marcó 1,686 créditos usados contra un límite de 800.
4. **El respaldo actual es pobre o inexistente según el consumidor.**

| Consumidor | Qué hace hoy cuando todos fallan |
|---|---|
| `PriceUpdateService` | Conserva `currentValue` de la posición |
| `PortfolioService.resolveCurrentAssetPrice` | Cae a `lastTransactionPrice`, que puede tener meses |
| `StockDataController` (`/stock`), `HybridQuoteService`, `DividendGrowthService` | Sin respaldo: error al cliente o rendimiento vacío |

5. **El precio no lleva fecha.** El valor en Redis es solo el número (`["java.math.BigDecimal",99.27000]`) y el puerto devuelve `Optional<BigDecimal>`; nadie puede saber de cuándo es el dato.

**Requisito del owner (15.11.2026):** con el mercado cerrado (noches, fines de semana, festivos) el sistema no debe consultar proveedores externos; si llega a consultar, debe conservar ese dato hasta que el mercado reanude.

**Propuesta del owner (15.11.2026):** `time_to_open` de `GET https://api.twelvedata.com/market_state` es una cuenta regresiva hasta la campana de apertura. Usarla como temporizador (TTL) de la llave Redis que guarda el último precio de cierre oficial, de modo que Redis la borre justo en la apertura y mientras exista no se consulte a ningún proveedor.

**Lo comprobado con la llave pública `demo` (domingo 15.11.2026):**

| Endpoint | Resultado |
|---|---|
| `market_state?code=XNYS` | Cerrada. `time_to_open` pasó de `10:18:52` a `09:40:30` entre dos consultas |
| `market_state?code=XMEX` | Cerrada. `time_to_open` pasó de 34:18:52 a `33:40:30` entre las mismas dos consultas |
| `eod?symbol=AAPL` | `datetime: 2026-11-13`, `close: 356.26999`, más `timestamp`, `exchange`, `mic_code`, `currency` |

- **Es una cuenta regresiva real:** ambas bolsas bajaron los mismos 38 min 22 s. El valor solo es válido en el instante en que se lee.
- **Puede pasar de 24 horas** (`33:40:30`): es una duración, no una hora del día.
- **Conoce festivos:** la BMV abre el martes 17, no el lunes 16 (Día de la Revolución). Coincide con la fila `MX_BMV` de `market_holiday`.
- **`/eod` trae la fecha de la sesión:** el viernes 13.11.2026, la última sesión completa. Eso permite comprobar que un precio es el cierre de la sesión correcta.
- Cada llamada cuesta 1 crédito de la misma cuota de TwelveData, hoy excedida (problema 3).
- **Verificado el 16.11.2026 con la API key propia** (`TwelveDataCredentialsIT`): `market_state` (XNYS y XMEX) y `/eod` responden con el contrato esperado.
- **No verificado:** comportamiento en cierres anticipados, a qué hora `/eod` pasa a la fecha del día, y si su `close` es el precio de la subasta de cierre o la última barra del día (el `timestamp` corresponde a las 15:59 ET).

**Fuera de este ADR:** el aviso "Datos parciales — sin precio: CRCL" del frontend sale de la cadena de histórico (`GET /me/portfolio/history`, `CachedMarketPriceHistoryAdapter`), no de esta ruta. Cripto opera 24/7 y no cambia.

## Decision

Se adopta la propuesta del owner. Dos llaves Redis propias de `marketdata`, delante de la cadena de proveedores:

| Llave | Contenido | Vida | Significado |
|---|---|---|---|
| `marketdata:quote:close:{SYM}` | `price`, `sessionDate`, `source`, `provider`, `fetchedAt`, `opensAt` | Hasta la campana de apertura | Mientras exista, **no se consulta a ningún proveedor** |
| `marketdata:quote:last:{SYM}` | `price`, `fetchedAt`, `provider` | 7 días, renovado en cada éxito | Respaldo cuando fallan todos los proveedores |
| `marketdata:market-state:{MARKET}` | `status`, `opensAt`, `expiresAt`, `checkedAt` | Hasta la campana de apertura (o `retry-interval` si no hubo dato) | Campana ya calculada para ese mercado (`US_EQUITY`, `MX_BMV`) |

### Lógica de negocio de la caché de cierre

**R1 — La cuenta regresiva se convierte en una hora fija al recibirla.**
`time_to_open` envejece cada segundo, así que nunca se guarda ni se reutiliza tal cual. Al llegar la respuesta se calcula la campana: `opensAt = instante de recepción + time_to_open`. Esa campana se guarda una vez por mercado y por periodo de cierre.

**R2 — Redis lleva su propia cuenta regresiva hacia la misma campana.**
La llave de cierre se escribe con `EXPIREAT opensAt`, y además guarda `opensAt` como campo: si el `EXPIREAT` no llegara a aplicarse, la lectura ignora una llave cuya campana ya pasó. Un símbolo congelado el viernes a las 16:05 y otro dado de alta el domingo expiran en el mismo instante, sin recalcular nada y sin volver a llamar a `market_state`.

**R3 — Solo se congela si todas las fuentes disponibles dicen "cerrado".**
- Calendario local dice abierto → flujo normal, sin consultar `market_state`.
- Calendario local dice cerrado y TwelveData dice abierto → no se congela; WARN y métrica de divergencia.
- Ambos dicen cerrado → campana = la más próxima de las dos.
- TwelveData no responde → decide el calendario local.

Ante cualquier desacuerdo gana la opción que consulta de más: llamar a proveedores con el mercado cerrado es inofensivo; congelar un precio durante una sesión real no lo es.

**R4 — El precio congelado debe ser el cierre de la sesión correcta.**
`time_to_open` dice cuánto falta para abrir, no si el precio ya es el cierre oficial. Se confirma por fecha:

1. *Sesión esperada* = fecha de la última sesión regular ya terminada, según el calendario local.
2. Se pide `/eod` del símbolo. Si su `datetime` es la sesión esperada, ese `close` se congela con `source = OFFICIAL_EOD`.
3. Si `/eod` aún trae la sesión anterior, falla o no está disponible, no se congela todavía: se devuelve el precio de la cadena como hoy y se reintenta en el siguiente vencimiento de `stockPrices`.
4. *Respaldo:* pasado `settleDelay` desde el cierre sin confirmación, se congela el precio de la cadena con `source = SETTLED_CHAIN`.

**R5 — Un precio de respaldo nunca se congela.**
Si la cadena falla y se sirve `quote:last`, no se escribe la llave de cierre. Por eso el temporizador no vive en `stockPrices`: esa caché no distingue un precio recién obtenido de uno de respaldo.

**R6 — La campana tiene tope.**
Si faltan ≤ 0 segundos o más de `max-hold`, no se congela. Protege de un `time_to_open` absurdo o mal formado.

**R7 — Todo es de mejor esfuerzo.**
Si Redis o TwelveData fallan, se registra WARN y la ruta sigue hacia la cadena de proveedores, como hoy.

**Estados de un símbolo:**

| Estado | Condición | Qué hace `getLatest` | Llamadas externas |
|---|---|---|---|
| En sesión | Calendario local: abierto | Cadena cada 15 min | Como hoy |
| Cierre sin confirmar | Cerrado, sin llave de cierre | Cadena cada 15 min e intenta confirmar con `/eod` | Como hoy + 1 crédito por intento |
| Cierre retenido | Existe `quote:close` | Devuelve la llave | Ninguna |

La llave expira en la campana y el símbolo vuelve a "En sesión".

**Flujo de `getLatest(symbol)` tras un fallo de `stockPrices`:**

```
held = store.findHeld(symbol)
si held existe                      → devolver held.price            (sin proveedores)

window = policy.closedWindow(market, now)       // vacío si abierto o hay divergencia
si window existe:
    eod = officialClose.find(symbol)            // TwelveData /eod
    si eod existe y eod.sessionDate == window.sessionDate:
        store.hold(symbol, eod, OFFICIAL_EOD, window.opensAt)
        store.saveLast(symbol, eod)
        devolver eod.price

price = cadena(symbol)              // éxito → store.saveLast
                                    // fallo total → quote:last si existe; si no, relanzar
si window existe y window.settled y price vino de un proveedor:
    store.hold(symbol, price, SETTLED_CHAIN, window.opensAt)
devolver price
```

**`policy.closedWindow(market, now)`:**

```
local = calendar.statusAt(market, now)
si local.isOpen                     → vacío                           (no consulta market_state)
opensAt = local.nextChangeAt
remote = stateCache.find(market) o remoteState.fetch(market)   // market-state:{MARKET}
si remote existe:
    si remote.open                  → WARN divergencia; vacío
    opensAt = min(opensAt, remote.opensAt)
si opensAt − now ≤ 0 o > maxHold    → vacío
sessionDate = última sesión regular terminada (statusAt + isTradingDay, sin métodos nuevos)
settled     = la sesión también estaba cerrada en now − settleDelay, con la misma próxima apertura
devolver ClosedWindow(sessionDate, opensAt, settled)
```

`ExternalProviderInvalidSymbolException` sigue cortando la cadena sin respaldo.

**Ejemplo: NYSE, viernes 13 a lunes 16 de noviembre**

| Hora (ET) | Qué pasa | Llamadas externas |
|---|---|---|
| Vie 15:45 | En sesión: cadena, `stockPrices` 15 min | Cotización |
| Vie 16:00 | Campana de cierre | — |
| Vie 16:05 | Primer vencimiento con mercado cerrado. `market_state` devuelve `65:25:00` → `opensAt` = lun 09:30. `/eod` trae fecha 13 → se congela con vida de 65 h 25 min | 1 `market_state` + 1 `/eod` |
| Vie 16:05 (alterno) | `/eod` aún trae fecha 12 → no se congela; precio de la cadena; reintento a los 15 min | 1 `/eod` + cotización |
| Vie 20:00 (alterno) | Sigue sin confirmar → se congela el precio de la cadena (`SETTLED_CHAIN`) | Cotización |
| Sáb y dom | Cada 15 min se lee la llave de cierre | Ninguna |
| Dom 18:00 | Alta de un símbolo nuevo: `/eod` trae fecha 13 → se congela hasta la misma campana | 1 `/eod` |
| Lun 09:30:00 | Redis borra las llaves de cierre | — |
| Lun 09:30+ | En sesión otra vez | Cotización |

**Estado remoto del mercado (`market_state`):**

- Se consulta por MIC (`US_EQUITY` → `XNYS`, `MX_BMV` → `XMEX`); el mapeo vive en el adapter. Es un dato por mercado, no por símbolo: **una llamada por mercado por periodo de cierre** (~5 créditos por semana por mercado).
- Una respuesta fallida, ilegible o que diga "abierto" se recuerda durante `retry-interval`, para no repetir la llamada en cada símbolo.
- `time_to_open` se interpreta como duración (`HH:MM:SS` con horas ≥ 24). Un valor mal formado equivale a "no disponible".
- La campana se calcula desde el instante previo a la petición, así que queda como mucho la latencia de red antes de la real, nunca después.
- Si `/eod` responde 429, 401 o 403 se deja de consultar durante `retry-interval`; un símbolo desconocido no bloquea a los demás.
- Usa la API key de `TwelveDataProperties`. Sin API key configurada, los adapters no existen: decide el calendario local y solo aplica el respaldo de R4.

**Mercado de cada símbolo:** en la Fase 1 todo lo que pasa por `StockQuoteOrchestrator` se evalúa contra `Market.US_EQUITY`, porque esa cadena solo cotiza listados de EE. UU. Cuando [[ADR-0016]] Fase 2 enrute por mercado, la política recibirá el mercado real.

**Piezas:**

| Pieza | Ubicación | Detalle |
|---|---|---|
| `StockQuote`, `ClosedWindow`, `RemoteMarketState` (records), `HeldQuoteSource` (enum) | `marketdata/domain/model` | Cotización con procedencia, periodo de cierre, estado remoto y origen del precio retenido |
| `LastKnownQuotePort` | `marketdata/application/port/out` | `findHeld`, `findLast`, `saveLast`, `hold(symbol, quote, source, opensAt)` |
| `MarketStateCachePort` | `marketdata/application/port/out` | `find(market)`, `save(market, state, expiresAt)` |
| `RemoteMarketStatePort` | `marketdata/application/port/out` | `fetch(market)`: estado abierto/cerrado y campana |
| `OfficialClosePort` | `marketdata/application/port/out` | `find(symbol)`: cierre con fecha de sesión |
| `LastKnownQuoteRedisAdapter` | `marketdata/infrastructure/outbound/redis` | Hashes `quote:close` y `quote:last`; `@ConditionalOnProperty` |
| `MarketStateRedisAdapter` | `marketdata/infrastructure/outbound/redis` | Hash `market-state:{MARKET}` |
| `TwelveDataMarketStateAdapter` | `marketdata/infrastructure/outbound/twelvedata` | `/market_state?code={MIC}` |
| `TwelveDataEndOfDayAdapter` | `marketdata/infrastructure/outbound/twelvedata` | `/eod?symbol={SYM}` |
| `ClosedMarketQuotePolicy` | `marketdata/application/service` | `closedWindow(market)`; usa `TradingCalendarService`, ambos puertos de estado y `Clock` |
| `StockQuoteOrchestrator` | existente | Recibe puertos y política por constructor; conserva el constructor de solo proveedores |
| `MarketDataProviderConfig` | existente | Declara los beans nuevos y los inyecta con `ObjectProvider` |

**Parámetros:**

| Propiedad | Default | Para qué |
|---|---|---|
| `marketdata.quote.last-known.enabled` | `true` | Apaga el último precio conocido (y con él la retención) |
| `marketdata.quote.last-known.max-age` | `P7D` | Vida de `quote:last` |
| `marketdata.quote.closed-market-hold.enabled` | `true` | Apaga solo la retención con mercado cerrado |
| `marketdata.quote.closed-market-hold.official-close.enabled` | `true` | Apaga la confirmación con `/eod`; queda solo el respaldo por margen |
| `marketdata.quote.closed-market-hold.settle-delay` | `PT4H` | Margen tras el cierre para congelar sin confirmación |
| `marketdata.quote.closed-market-hold.max-hold` | `P5D` | Tope de la vida de la llave de cierre |
| `marketdata.market-state.remote.enabled` | `true` | Apaga la consulta a `market_state`; decide el calendario local |
| `marketdata.market-state.remote.retry-interval` | `PT1H` | Espera tras un fallo o una divergencia antes de volver a consultar |

**Reglas operativas:**

- `fetchedAt` es la hora en que el backend obtuvo el dato; `sessionDate` es la sesión a la que pertenece el precio.
- Ningún token ni URL con credenciales en logs: la API key viaja en la query, así que solo se registran símbolo, MIC, proveedor, fecha de sesión y duración.
- Para forzar un refresco durante el cierre basta borrar la llave `quote:close` del símbolo.

**Fases:**

- **Fase 1 (este ADR):** último precio conocido + retención del cierre oficial para la ruta USD. No cambia contratos REST ni el puerto `MarketDataProvider`.
- **Fase 1b (este ADR, cambio separado):** ruta MXN de `HybridQuoteService`. Ver "Ruta MXN".
- **Fase 2 (decisión aparte):** exponer `priceAsOf` / `priceStale` al frontend en las respuestas de holdings, usando `sessionDate` y `source`.

### Ruta MXN (Fase 1b)

`HybridQuoteService` cotiza MXN por DataBursatil con reglas propias, y también ignora el calendario:

- **El refresco programado no tiene zona horaria.** El cron `0 */5 9-15 * * MON-FRI` se evalúa en la zona de la JVM, que `Dockerfile.secure` y `docker-compose` fijan en UTC. En UTC eso equivale a 03:00–09:59 de Ciudad de México, mientras la sesión de la BMV es 08:30–15:00. Corrección: `zone = "America/Mexico_City"`, horas alineadas a la sesión y guarda `isTradingDay(MX_BMV)` para festivos. (Pendiente de confirmar la zona real del contenedor en Render.)
- **La consulta a demanda siempre llama a DataBursatil.** Con la BMV cerrada debe servir el último registro de `market_price_snapshot`, que ya existe y ya tiene consulta de "último por símbolo". Decide la misma `closedWindow`, con `Market.MX_BMV` y el MIC `XMEX`.

## Options Considered

### Option A: Llave de cierre propia que expira en la campana; temporizador de TwelveData acotado por el calendario local; cierre confirmado por fecha (recomendada)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media: tres puertos, dos adapters, una política, cambio acotado en el orquestador |
| Cobertura | Todos los consumidores de `getLatest`, sin tocarlos |
| Llamadas con mercado cerrado | Cero una vez capturado el cierre |
| Costo en créditos | ~1 por mercado y ~1 por símbolo por periodo de cierre |
| Si TwelveData falla | Decide el calendario local y se congela por margen; la retención sigue funcionando |

**Pros:** es la propuesta del owner con sus puntos débiles cubiertos; dos fuentes de calendario se vigilan entre sí; el cierre se confirma con un dato, no con una suposición de horario; la cuenta regresiva es visible en RedisInsight. **Cons:** más piezas que mantener; si Redis se cae, no hay retención ni respaldo.

### Option B: La propuesta literal — TTL de `stockPrices` igual a `time_to_open`

Técnicamente posible: Spring Data Redis 4.0 (el proyecto usa Spring Boot 4.0.8) admite TTL por entrada (`RedisCacheWriter.TtlFunction`, comprobado en el jar).

**Pros:** una sola llave; con mercado cerrado ni siquiera se ejecuta el método. **Cons:** la función de TTL solo recibe llave y valor, así que no distingue un precio de proveedor de uno de respaldo ni sabe de qué sesión es; `CacheConfig` vive en `shared` y necesitaría un puerto nuevo hacia `marketdata`; no aporta respaldo por fallo.

### Option F: `time_to_open` como única fuente, sin calendario local

**Pros:** no hay que mantener `market_holiday` para esta ruta. **Cons:** con la cuota agotada (situación de hoy) no hay temporizador y no se retiene nada; un error de TwelveData congelaría el precio durante una sesión real sin nada que lo detecte.

### Option G: Solo calendario local y margen fijo (versión anterior de este ADR)

**Pros:** cero créditos, sin dependencia externa. **Cons:** una fila de festivo errónea o faltante pasa inadvertida; el margen fijo es una suposición sobre cuándo publican los proveedores.

### Option C: Condicionar el `@Scheduled` de `PriceUpdateService` al calendario

**Pros:** trivial. **Cons:** solo frena el refresco programado; una consulta del usuario en domingo sigue llegando a proveedores.

### Option D: Tabla `market_price_snapshot` en Postgres como almacén del último precio USD

**Pros:** durable aunque Redis se pierda. **Cons:** una escritura por símbolo cada 15 minutos en una tabla pensada como histórico OHLC. Sí se usa para MXN, donde ya es el almacén.

### Option E: Solo circuit breaker (Resilience4j)

**Descartada:** frena reintentos pero no entrega precio ni distingue mercado cerrado.

## Trade-off Analysis

**Retener el precio durante el cierre cambia la dirección del error.** Hoy un dato equivocado se corrige solo a los 15 minutos; congelado, dura hasta la apertura. R4 reduce ese riesgo: con `/eod` disponible, el precio se congela solo si su fecha es la sesión esperada. El riesgo queda limitado al respaldo por margen (`SETTLED_CHAIN`), que sigue siendo una suposición y por eso queda marcado en la llave y en la métrica.

**Dos fuentes de calendario en lugar de una.** Con una sola, su error es silencioso. Con dos y la regla R3, cualquier desacuerdo cae del lado seguro y queda registrado.

**TwelveData gana dos papeles más** (estado del mercado y cierre oficial) sobre la misma cuota. El gasto nuevo es pequeño frente a lo que evita, pero si la cuota se agota se pierden la segunda opinión y la confirmación por fecha; la retención continúa con calendario local y margen.

**La sesión esperada sale del calendario local.** Si falta un festivo en `market_holiday`, ese día el calendario dice "abierto" y no se congela (inofensivo). Sigue haciendo falta sembrar 2027: `market_state` informa el estado actual, no la lista de festivos futuros.

**Servir un precio viejo ante un fallo** es un riesgo en una aplicación financiera, pero la alternativa actual es peor: `lastTransactionPrice` de hace meses o un error. La antigüedad queda acotada a 7 días y visible en logs y métricas.

**Se congela por sesión regular, no por horario extendido.** La valuación usa el cierre oficial y los planes actuales no entregan precios de pre-market ni after-hours.

## Consequences

**Más fácil:**
- Fines de semana y festivos: cero llamadas de cotización para símbolos ya conocidos.
- Días hábiles (estimado): de ~96 llenados por símbolo a ~28 si `/eod` confirma poco después del cierre, o ~43 si se llega al respaldo por margen. Con 7 símbolos, de ~4,700 llenados por semana a entre ~1,000 y ~1,500, más ~40 créditos semanales de `market_state` y `/eod`.
- Un fallo total de proveedores deja de producir errores mientras exista un precio de los últimos 7 días, y deja de multiplicar reintentos.
- El dato en Redis lleva sesión, origen y proveedor, y su TTL muestra cuánto falta para la campana.
- Un error en el calendario local deja de ser silencioso.

**Más difícil / a vigilar:**
- Un precio congelado por margen (`SETTLED_CHAIN`) puede ser el de la sesión anterior. Mitigación: confirmación por fecha, interruptor y borrado manual de la llave.
- Mientras `/eod` no confirme, cada intento cuesta 1 crédito por símbolo (hasta ~16 por símbolo en el peor día). `retry-interval` aplica también a fallos de `/eod`.
- Hay que mantener `market_holiday` al día: sembrar 2027 antes del 1 de enero.
- Un símbolo nunca consultado con éxito sigue sin respaldo ante un fallo total.
- Las llaves no distinguen mercado ni moneda, igual que `stockPrices`. Debe cambiar junto con [[ADR-0016]] Fase 2.
- Métricas: `marketdata.quote.served{source=provider|official_close|held_market_closed|last_known_fallback}` y `marketdata.market-state.divergence{market,kind=open|opens-at}`.

**A revisitar:**
- Mismo patrón para el histórico (`CachedMarketPriceHistoryAdapter`): no recalcular series diarias con mercado cerrado y servir la última serie conocida ante un fallo.
- Si el `close` de `/eod` difiere del cierre de EODHD o del broker, decidir cuál es la referencia.

## Action Items

### Fase 1 — ruta USD
1. [x] Verificar con la API key propia que `market_state` y `/eod` están disponibles en el plan (`TwelveDataCredentialsIT`, 16.11.2026).
2. [x] Modelos de dominio, puertos, adapters Redis y adapters de Twelve Data.
3. [x] `ClosedMarketQuotePolicy`, flujo en `StockQuoteOrchestrator` y cableado en `MarketDataProviderConfig`; propiedades en `application.properties` (apagadas en el perfil `test`).
4. [x] Unit: `ClosedMarketQuotePolicyTest` (16), `StockQuoteOrchestratorRetentionTest` (10), `TwelveDataMarketStateAdapterTest` (10), `TwelveDataEndOfDayAdapterTest` (7); `MarketDataProviderConfigTest` actualizado.
5. [x] IT con Redis real: `ClosedMarketQuoteRedisAdaptersIT` (7) y, de extremo a extremo con Spring, `StockQuoteClosedMarketHoldIT` (2).
6. [x] `ModulithVerificationTest`, ArchUnit de `marketdata`/`shared` y `spotless:check` en verde.
7. [ ] Medir en un día hábil a qué hora `/eod` pasa a la fecha del día (5, 30 y 60 min tras el cierre) y qué devuelve la cadena en ese lapso; ajustar `settle-delay` con ese dato.
8. [ ] Observar `time_to_open` el 27.11.2026 (cierre anticipado, 13:00 ET).
9. [ ] Sembrar festivos 2027 de US y BMV en `market_holiday` (migración Flyway).
10. [ ] Verificación en prod: un fin de semana sin líneas de proveedor para STOCK en los logs de Render.

### Fase 1b — ruta MXN
11. [ ] Confirmar la zona horaria de la JVM en Render.
12. [ ] `HybridQuoteService.scheduledRefresh`: `zone` explícita, horas de sesión BMV y guarda `isTradingDay(MX_BMV)`.
13. [ ] `HybridQuoteService.getMxnPrice`: con BMV cerrada, servir el último `market_price_snapshot` del periodo de cierre.
14. [ ] Tests unitarios de ambos casos en `HybridQuoteServiceTest`.
