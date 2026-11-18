# ADR-0034: Almacén permanente de velas diarias con cobertura incremental por activo

**Status:** Proposed
**Date:** 2026-11-16
**Deciders:** Aristeo (owner)
**Módulo:** `portfolio` (cadena de histórico `CachedMarketPriceHistoryAdapter` + `MarketPriceHistoryProvider`), con dos puertos de `marketdata` (calendario y splits).
**Relacionado:** [[ADR-0009]] (`fx_rate_daily`: FIX diario inmutable y persistente), [[ADR-0011]] (splits), [[ADR-0012]] (calendario), [[ADR-0016]] (proveedores), [[ADR-0020]] (histórico inmutable de dividendos), [[ADR-0021]] (cadena con fallback), [[ADR-0033]] (cierre retenido y última sesión terminada).

## Context

Una vela diaria de una sesión ya cerrada no cambia. El sistema no lo aprovecha.

**Qué hace hoy la cadena de histórico (verificado en código, 16.11.2026):**

- **Solo consulta bajo demanda**, cuando alguien abre una gráfica (`/me/portfolio/history` y el histórico por activo). No descarga al registrar una compra ni refresca periódicamente mientras la posición está abierta.
- **No persiste nada en Postgres.** Guarda la serie completa en Redis bajo una llave por rango (`price:STOCK:GOOGL:6M`) con vida fija: 1 hora (1D), 6 horas (1S a 1Y) o 24 horas (ALL). Al vencer, la siguiente vista vuelve a descargar la serie entera.
- **Cada rango es una descarga independiente.** 1M, 6M y 1Y del mismo activo son tres llaves y tres llamadas.
- **No mira el calendario.** Un sábado se vuelve a descargar una serie que no puede tener velas nuevas.
- **Un fallo no deja nada guardado**: error o "Datos parciales — sin precio: CRCL", y reintento en cada vista.
- **Solo conserva el cierre** (`PricePoint(time, price)`), no OHLC ni volumen.

**Cadena de proveedores para STOCK/ETF/INDEX y lo que admite cada uno:**

| Orden | Proveedor | Ventana de fechas | Observación |
|---|---|---|---|
| 4 | EODHD `/api/eod` | `from` / `to` | Plan gratuito: 1 año de historia y 20 llamadas al día (documentación de EODHD) |
| 5 | TwelveData `/time_series` | Solo `outputsize` (últimas N velas) | El plan gratuito rechaza `start_date` / `end_date` (comentario en el adapter) |
| 10 | Massive `/v2/aggs/.../range/1/day/{from}/{to}` | `from` / `to` | Hoy se pide con `adjusted=true` |
| 20 | AlphaVantage `TIME_SERIES_DAILY` | `compact` / `full` | 25 llamadas al día |

**Hallazgo: la serie que hoy se guarda no es inmutable.** El adapter de EODHD lee `adjusted_close`. Según la documentación de EODHD, ese campo está ajustado por splits **y por dividendos**, mientras que `open/high/low/close` son precios crudos. Un precio ajustado por dividendos cambia hacia atrás cada vez que la emisora paga. Dos consecuencias:

1. Guardar ese valor de forma permanente produciría saltos en las uniones entre lo descargado antes y después de un pago.
2. [[ADR-0011]] asume un feed ajustado solo por splits ("se ajusta la cantidad, no el precio"). Con `adjusted_close` el valor histórico de una emisora que paga dividendos queda subestimado. Es un defecto actual, independiente de este ADR.

El dato que de verdad no cambia es el **cierre crudo**.

**Precedentes en el proyecto:** `fx_rate_daily` guarda el FIX de cada día de forma permanente y trae solo lo que falta ([[ADR-0009]]); el histórico de dividendos es inmutable ([[ADR-0020]]); el precio de una fecha concreta (`historicalPrices`) se cachea 10 años.

**Escenario del owner (GOOGL):**

| Usuario | Periodo pedido |
|---|---|
| Raúl | 2020-02-28 → 2021-02-28 |
| Juan | 2021-09-02 → 2022-04-30 |
| Mau | 2020-04-02 → 2023-01-04 |

Mau necesita datos que en su mayoría ya se descargaron para Raúl y Juan, pero el sistema los vuelve a pedir completos. Entre Raúl y Juan queda un hueco (2021-03-01 → 2021-09-01) que nadie pidió.

**Propuesta del owner:** registrar por activo los rangos ya descargados, calcular la intersección con el rango pedido y descargar solo los periodos faltantes.

**Fuerzas:**

- Las llamadas a proveedores deben crecer con el número de activos distintos y de sesiones nuevas, no con el número de usuarios ni de vistas.
- Los proveedores cobran por llamada, no por fila: una serie diaria de 5 años es una sola petición.
- En el producto actual casi todas las gráficas piden `[inicio, ahora]`.

## Decision

Guardar en Postgres el **cierre crudo de cada sesión terminada**, una sola vez por activo y fecha, con un registro de **cobertura contigua** por activo. Ante una petición se descarga solo lo que falta para extender esa cobertura; el ajuste por splits se calcula al leer.

Se adopta la propuesta del owner con una simplificación, confirmada por el owner el 16.11.2026: la cobertura es un único intervalo por activo, no un conjunto de rangos. Cuando una petición cae fuera de lo cubierto, se descarga hasta el borde de lo que ya existe, de modo que nunca se forman huecos.

### Reglas

**D1 — Solo se guardan sesiones terminadas.** La vela del día en curso no entra al almacén. "Sesión terminada" lo decide el calendario de [[ADR-0012]], con la misma lógica de última sesión de [[ADR-0033]].

**D2 — Se guarda el cierre crudo.** Sin ajuste por splits ni dividendos. Si un proveedor solo puede entregar precios ajustados, su serie se sirve pero no se persiste.

**D3 — La cobertura es un intervalo contiguo por activo: `[covered_from, covered_to]`.** Significa "se preguntó a la cadena por todas las sesiones de este intervalo y lo que existe está guardado".

**D4 — Cálculo de lo faltante.** Para una petición `[inicio, fin]`, con `fin` recortado a la última sesión terminada:

| Situación | Qué se descarga |
|---|---|
| Sin cobertura | `[inicio, fin]` |
| `inicio` anterior a `covered_from` | `[inicio, covered_from − 1]` |
| `fin` posterior a `covered_to` | `[covered_to + 1, fin]` |
| Dentro de la cobertura | Nada |

Como máximo dos llamadas; lo habitual es cero o una. Si la petición empieza después de `covered_to`, la regla de la tercera fila ya rellena lo intermedio.

**D5 — Escritura atómica.** Las velas y la nueva cobertura se guardan en la misma transacción. Si el proveedor falla, la cobertura no cambia y se sirve lo que ya existe.

**D6 — Límite de profundidad del proveedor.** Si se pide hacia atrás y la cadena no tiene datos tan antiguos, la cobertura igualmente avanza hasta el `inicio` pedido: ya se sabe que no hay nada más, y no se vuelve a preguntar.

**D7 — Splits al leer.** Precio servido = cierre crudo ÷ producto de los splits con fecha posterior a la vela (`StockSplitPort`). Los consumidores reciben la misma serie ajustada por splits que esperan hoy; un split nuevo no obliga a borrar ni a descargar nada.

**D8 — El punto de hoy sale de la cotización en vivo.** Con el mercado abierto, o con la sesión de hoy aún no guardada, el último punto de la gráfica es `MarketDataProvider.getLatest` ([[ADR-0033]]). No se llama a un proveedor de histórico para el día en curso.

**D9 — Concurrencia.** Se reutiliza el candado de carga por activo que ya existe en Redis, y la inserción ignora filas ya presentes (llave primaria por activo y fecha), como hace `fx_rate_daily`.

**D10 — Redis sigue delante.** La caché de series por rango se conserva. Su vencimiento pasa a costar una lectura a Postgres en lugar de una llamada a un proveedor.

**D11 — Dos disparadores, un solo servicio.** La cobertura se extiende en dos momentos, ambos por la misma ruta:

- **Al registrar una compra** (precarga). `transaction` ya publica `TransactionRegisteredEvent` para STOCK/ETF ([[ADR-0024]]). Un listener asíncrono en `portfolio`, tras el commit, pide cobertura desde la primera operación de ese símbolo hasta la última sesión terminada. No bloquea ni revierte el registro del usuario; si falla, no pasa nada: la vista lo resolverá.
- **Al abrir una gráfica** (garantía). Cubre lo que la precarga no puede: las sesiones nuevas de cada día, las posiciones registradas antes de este cambio y cualquier precarga fallida.

Una importación masiva genera muchos eventos del mismo símbolo; el candado por activo (D9) hace que solo el primero descargue.

**D12 — Dos categorías de dato.**

| Categoría | Qué es | Dónde vive | Se vuelve a pedir |
|---|---|---|---|
| Consolidado | Sesión terminada cuya vela ya se recibió | `daily_price_bar` | No, salvo D14 |
| Pendiente | El día en curso, y sesiones terminadas cuya vela aún no se recibió | No se guarda; hoy sale de la cotización en vivo (D8) | Sí, hasta consolidarse |

**D13 — La cola avanza solo con velas recibidas.** Al extender hacia adelante se pide `[covered_to + 1, última sesión terminada]`, pero `covered_to` avanza únicamente hasta la fecha de la última vela que el proveedor devolvió. Si el proveedor aún no publica el cierre de hoy, o el calendario local cree que hubo sesión un día que no la hubo, esa fecha queda pendiente y se reintenta; no se marca como "no existe". Es la diferencia con D6, que aplica solo hacia atrás.

Ejemplo con cobertura `[2020-02-28, 2026-11-13]` (viernes):

| Momento | Última sesión terminada | Se pide | Resultado |
|---|---|---|---|
| Sábado 14 o domingo 15 | Viernes 13 | Nada | Sin llamadas |
| Lunes 16, sesión abierta | Viernes 13 | Nada | Hoy sale de la cotización en vivo |
| Lunes 16 tras el cierre, el proveedor ya publicó | Lunes 16 | 2026-11-14 → 2026-11-16 | Llega una vela (16); `covered_to` = 16 |
| Lunes 16 tras el cierre, el proveedor aún no publica | Lunes 16 | 2026-11-14 → 2026-11-16 | No llega vela; `covered_to` sigue en 13; se reintenta |
| Un mes sin que nadie consulte el activo | — | Del 14 de noviembre a la última sesión | Una sola llamada trae todo el mes |

El tamaño de cada descarga depende de cuántas sesiones faltan, no del tamaño del histórico acumulado.

**D14 — Volver a descargar lo consolidado es una operación excepcional y explícita.**

| Situación | Tratamiento |
|---|---|
| Split o reverse split | Ninguno: se guarda el cierre crudo y el ajuste se calcula al leer (D7) |
| Ajuste por dividendos | Ninguno: el cierre crudo no se ajusta por dividendos (D2) |
| Corrección oficial del proveedor | Restablecimiento manual del activo o de un rango (endpoint admin) |
| Migración entre proveedores | Restablecimiento por proveedor: cada vela guarda de quién vino |
| Reconciliación | Tarea opcional que vuelve a pedir las últimas N sesiones y compara; apagada por defecto porque gasta llamadas |

Un restablecimiento borra las velas y recorta la cobertura del tramo afectado; la siguiente petición lo descarga de nuevo por la ruta normal.

**D15 — La actualización diaria es perezosa; no hay descarga nocturna de todos los activos.** Decisión del owner (17.11.2026). La cola se extiende cuando alguien consulta el activo o registra una compra (D11), de modo que las llamadas dependen de los activos que se miran, no de los que existen.

Queda una **tarea opcional, apagada por defecto**, para tener la vela lista antes de la primera vista:

| Aspecto | Regla |
|---|---|
| Qué activos | Solo STOCK/ETF con posición abierta (cantidad mayor que cero en `portfolio_entry`), un símbolo distinto una sola vez aunque lo tengan muchos usuarios |
| Qué hace | Llama a la misma `ensureCovered` que la vista; si la cobertura ya está al día, no hay llamada al proveedor |
| Cuándo | Una vez por día hábil, tras el cierre más un margen, con zona horaria explícita del mercado; no corre en fines de semana ni festivos |
| Límites | Tope de símbolos por ejecución y pausa entre llamadas, para respetar la cuota por minuto y por día de cada proveedor |
| Si falla un símbolo | Se registra y se sigue con el siguiente; la vista lo resolverá (D13) |
| Varias instancias | El candado por activo (D9) evita descargas dobles |

| Propiedad | Default |
|---|---|
| `portfolio.price-history.daily-refresh.enabled` | `false` |
| `portfolio.price-history.daily-refresh.cron` | `0 0 18 * * MON-FRI` |
| `portfolio.price-history.daily-refresh.zone` | `America/New_York` |
| `portfolio.price-history.daily-refresh.max-symbols` | `50` |
| `portfolio.price-history.daily-refresh.delay-between-calls` | `PT8S` |

Con el margen de las 18:00 ET y una pausa de 8 segundos, 50 símbolos tardan unos 7 minutos y caben en el límite de 8 peticiones por minuto del plan gratuito de TwelveData.

### Flujo

```
Registro de compra  ──┐
                      ├──▶  DailyPriceHistoryStore.ensureCovered(símbolo, inicio, fin)
Vista de gráfica   ──┘                │
                                      ▼
                        Detectar lo faltante (D4)
                                      │
                    ┌─────────────────┴─────────────────┐
                    ▼                                   ▼
              Nada falta                     Descargar solo lo faltante
                    │                                   │
                    │                                   ▼
                    │                     Guardar cierres crudos (D2)
                    │                                   │
                    │                                   ▼
                    │                     Extender la cobertura (D3, D5)
                    └─────────────────┬─────────────────┘
                                      ▼
                  Leer de Postgres, ajustar splits (D7), añadir hoy (D8)
```

Es el flujo propuesto por el owner. Con cobertura contigua, "detectar huecos" devuelve como máximo dos tramos y "fusionar intervalos" siempre deja uno, porque cada descarga llega hasta el borde de lo ya cubierto.

### El escenario del owner con esta decisión

| Petición | Cobertura antes | Se descarga | Cobertura después |
|---|---|---|---|
| Raúl: 2020-02-28 → 2021-02-28 | — | 2020-02-28 → 2021-02-28 | 2020-02-28 → 2021-02-28 |
| Juan: 2021-09-02 → 2022-04-30 | 2020-02-28 → 2021-02-28 | 2021-03-01 → 2022-04-30 | 2020-02-28 → 2022-04-30 |
| Mau: 2020-04-02 → 2023-01-04 | 2020-02-28 → 2022-04-30 | 2022-05-01 → 2023-01-04 | 2020-02-28 → 2023-01-04 |
| Teo: compra el 2023-09-02, posición abierta | 2020-02-28 → 2023-01-04 | 2023-01-05 → última sesión | 2020-02-28 → última sesión |
| Usuario nuevo: 2020-01-01 → hoy | 2020-02-28 → última sesión | 2020-01-01 → 2020-02-27 | 2020-01-01 → última sesión |

Cinco llamadas en total y ninguna repetida; con varios intervalos serían siete. Las peticiones de Juan y de Teo traen meses que ellos no necesitan, en la misma llamada y sin costo adicional; a cambio, Mau y el usuario nuevo necesitan una llamada en lugar de dos. A partir de Teo, la cobertura avanza una sesión por día hábil mientras alguien consulte el activo.

### Modelo de datos

```sql
CREATE TABLE daily_price_bar (
    asset_type  VARCHAR(20)    NOT NULL,
    symbol      VARCHAR(30)    NOT NULL,
    bar_date    DATE           NOT NULL,
    close       NUMERIC(20, 8) NOT NULL,   -- cierre crudo de la sesion
    provider    VARCHAR(40)    NOT NULL,
    fetched_at  TIMESTAMPTZ    NOT NULL,
    PRIMARY KEY (asset_type, symbol, bar_date)
);

CREATE TABLE daily_price_coverage (
    asset_type    VARCHAR(20) NOT NULL,
    symbol        VARCHAR(30) NOT NULL,
    covered_from  DATE        NOT NULL,
    covered_to    DATE        NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL,   -- bloqueo optimista
    PRIMARY KEY (asset_type, symbol)
);
```

La llave primaria de `daily_price_bar` ya sirve para la lectura por rango; no hace falta otro índice.

### Piezas

| Pieza | Ubicación | Detalle |
|---|---|---|
| `DailyPriceBar`, `DailyPriceCoverage` (entidades) | `portfolio/domain/model` | Tablas anteriores |
| `DailyPriceBarRepository`, `DailyPriceCoverageRepository` | `portfolio/domain/repository` | Lectura por rango, inserción que ignora duplicados |
| `DailyPriceHistoryStore` | `portfolio/application/service` | D3 a D6: calcula lo faltante, descarga, guarda, lee |
| `MarketPriceHistoryProvider.fetchDailyCloses(symbol, from, to)` | interfaz existente | Método nuevo; devuelve cierres crudos. Por defecto, vacío ("no puedo dar crudo") |
| Adapters EODHD, Massive, AlphaVantage, TwelveData | existentes | Implementan el método nuevo según lo que admita cada API |
| `CachedMarketPriceHistoryAdapter` | existente | Para series diarias de STOCK/ETF/INDEX usa el almacén en lugar de la cadena directa |
| `PriceHistoryPrewarmOnTransactionListener` | `portfolio/infrastructure/inbound/event` | D11: escucha `TransactionRegisteredEvent` (módulo `shared`), asíncrono y tras el commit; interruptor `portfolio.price-history.prewarm-on-registration.enabled` |
| `PriceHistoryAdminController` | `portfolio/infrastructure/inbound/rest` | D14: `POST /api/v1/admin/price-history/{symbol}/reset` (ROLE_ADMIN), con rango y proveedor opcionales |
| `TradingSessionQueryPort` | `marketdata/application/port/out` | Puerto nuevo: última sesión terminada de un mercado |
| `StockSplitPort` | `marketdata/application/port/out` | Existente; ajuste por splits al leer |

### Alcance

- **Fase 1:** velas diarias de STOCK, ETF e INDEX en USD.
- **Fuera de la Fase 1:** velas intradía (rango 1D), cripto, BMV/MXN, OHLC y volumen.

## Options Considered

### Option A: Conjunto de rangos por activo (propuesta del owner)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media-alta: unir, restar y fusionar intervalos; varias filas de cobertura por activo |
| Llamadas en el escenario | 7 (Mau y el usuario nuevo necesitan dos cada uno: una por hueco) |
| Almacenamiento | El mínimo posible |
| Riesgo | Huecos permanentes; más casos límite en concurrencia |

**Pros:** nunca descarga una fecha que nadie pidió. **Cons:** ahorra filas, que son baratas, a costa de llamadas, que son lo caro; más lógica que probar.

### Option B: Cobertura contigua extendida bajo demanda (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media: dos fechas por activo y dos comparaciones |
| Llamadas en el escenario | 5 |
| Almacenamiento | Algo mayor: rellena lo intermedio |
| Riesgo | Bajo: sin huecos por construcción |

**Pros:** misma reutilización con menos llamadas y menos lógica; encaja con el uso real (`[inicio, ahora]`). **Cons:** puede guardar años que nadie mira.

### Option C: Descarga completa en el primer uso más tarea nocturna

**Pros:** la más simple de razonar; después solo se añade la cola diaria. **Cons:** los planes limitan la profundidad (EODHD gratuito, 1 año); la tarea nocturna gasta llamadas en activos que nadie consulta; con cientos de activos el arranque es costoso.

### Option G: Descarga nocturna de todos los activos del catálogo

**Descartada por el owner (17.11.2026):** las llamadas crecen con los activos que existen y no con los que se usan (500 activos serían 500 llamadas diarias aunque nadie los mire); supera las cuotas de los planes actuales.

### Option D: Sin tabla de cobertura; deducirla de las velas y el calendario

**Pros:** una tabla menos. **Cons:** no distingue "no existe dato" de "nunca se pidió" (activo que aún no cotizaba, suspensión); exige un calendario histórico completo y hoy `market_holiday` solo cubre 2026; provoca reintentos eternos.

### Option E: Solo Redis con vida larga o sin vencimiento

**Pros:** casi sin código. **Cons:** Redis es volátil y externo; sigue guardando una serie por rango; no resuelve la descarga incremental ni el fallo de proveedor.

### Option F: Base de series temporales (TimescaleDB u otra)

**Descartada por ahora:** el volumen previsto cabe holgadamente en una tabla de Postgres; añadiría infraestructura sin necesidad.

## Trade-off Analysis

**Filas contra llamadas.** La opción A minimiza almacenamiento; la B minimiza llamadas. Una serie diaria ocupa unas 252 filas por activo y año: 500 activos con 10 años son alrededor de 1.3 millones de filas (estimación). Las llamadas, en cambio, están limitadas a decenas o cientos por día en los planes actuales. Conviene gastar filas.

**Crudo más ajuste al leer, contra guardar lo ajustado.** Guardar el precio ajustado es más simple, pero ese valor no es inmutable y obligaría a borrar y volver a descargar en cada split, además de dejar saltos con feeds ajustados por dividendos. Guardar el crudo hace que el almacén sea realmente permanente; a cambio, la corrección depende de que la tabla local de splits esté completa. Esa dependencia ya existe hoy para ajustar cantidades.

**Cambio visible en las gráficas.** Al dejar `adjusted_close`, el histórico de emisoras que pagan dividendos subirá ligeramente en fechas pasadas. Es una corrección, pero hay que validarla contra un caso real antes de desplegar.

**Corrección tardía de un proveedor.** Una vela guardada no se vuelve a pedir. Si un proveedor corrige un cierre días después, el almacén conserva el valor anterior. Es poco frecuente; se cubre con un restablecimiento manual por activo.

**Dónde vive.** Un almacén de velas es dato de mercado y por naturaleza pertenece a `marketdata`, pero la cadena de proveedores de histórico vive en `portfolio`. Moverla es un cambio mayor que no aporta al objetivo; la Fase 1 se queda en `portfolio`.

## Consequences

**Más fácil:**
- Las llamadas de histórico pasan a depender de activos distintos y sesiones nuevas: como máximo una por activo y día hábil, y solo si alguien lo consulta.
- Fines de semana y festivos: cero llamadas de histórico.
- 1M, 6M, 1Y y ALL del mismo activo salen de las mismas filas.
- Un fallo de proveedor deja de vaciar la gráfica: se sirve lo guardado y el punto de hoy sale de la cotización en vivo.
- Se forma un repositorio histórico propio que crece con el uso.

**Más difícil / a vigilar:**
- Dos tablas nuevas y una migración; hay que vigilar su tamaño.
- La tabla de splits pasa a ser crítica para la gráfica, no solo para las cantidades.
- Un activo cuyo proveedor solo entrega precios ajustados no se beneficia: se sirve como hoy.
- Si se añade un proveedor con más profundidad, los activos ya cubiertos no la aprovechan sin un restablecimiento.
- La llave es tipo y símbolo, igual que la caché actual; no distingue mercado ni moneda. Debe cambiar junto con [[ADR-0016]] Fase 2 y [[ADR-0019]].

**A revisitar:**
- Cripto diario (sesión de 00:00 a 00:00 UTC) y BMV/MXN con el mismo almacén.
- OHLC y volumen cuando exista una gráfica de velas.
- Mover la cadena de histórico y el almacén a `marketdata`.
- Vida de la caché Redis de series: podría expirar en la siguiente campana de cierre en lugar de a las 6 horas.

## Action Items

### Antes de escribir código
1. [ ] Verificar por proveedor que entrega cierre crudo: EODHD `close`, Massive con `adjusted=false`, AlphaVantage `4. close`, TwelveData (parámetro de ajuste sin confirmar).
2. [ ] Confirmar la profundidad de historia del plan real de cada proveedor.
3. [ ] Medir en un activo con dividendos (KO) la diferencia entre `adjusted_close` y `close`, y decidir si la corrección de [[ADR-0011]] se despliega junto con este cambio.

### Fase 1 — STOCK/ETF/INDEX diario
4. [ ] Migración Flyway: `daily_price_bar` y `daily_price_coverage`.
5. [ ] Entidades y repositorios; inserción que ignora duplicados.
6. [ ] `TradingSessionQueryPort` en `marketdata` (reutiliza la lógica de última sesión de [[ADR-0033]]).
7. [ ] `fetchDailyCloses` en la interfaz y en los cuatro adapters.
8. [ ] `DailyPriceHistoryStore`: reglas D3 a D7.
9. [ ] `CachedMarketPriceHistoryAdapter`: usar el almacén para series diarias; punto de hoy desde la cotización en vivo (D8).
10. [ ] `PriceHistoryPrewarmOnTransactionListener` (D11): inicio = primera operación del símbolo; fallo contenido; apagado en el perfil `test`. Unit: una ráfaga de eventos del mismo símbolo descarga una sola vez.
11. [ ] Unit del almacén: sin cobertura; extensión hacia atrás; extensión hacia adelante; petición dentro de la cobertura; petición posterior con relleno intermedio (escenario Raúl/Juan/Mau/Teo); fin de semana sin llamadas; sesión abierta sin llamadas; cierre aún no publicado (la cola no avanza y se reintenta); un mes sin consultas (una sola llamada); fallo de proveedor sin cambio de cobertura; límite de profundidad; split posterior a la vela.
12. [ ] IT con Postgres (`InfraIntegrationTest`): escritura concurrente del mismo activo, transacción atómica.
13. [ ] `*CredentialsIT` de cada proveedor: contrato del cierre crudo; ejecutar `mvn test -Dtest=*CredentialsIT`.
14. [ ] `ModulithVerificationTest`, ArchUnit y `./mvnw spotless:apply` en verde.
15. [ ] Métrica `marketdata.pricehistory.store{outcome=hit|extended|pending|provider_failed}` y verificación en prod de un fin de semana sin llamadas de histórico.

### Fase 2 — operación
16. [ ] Endpoint admin de restablecimiento por activo, rango y proveedor (D14), con auditoría.
17. [ ] Tarea opcional de reconciliación de las últimas N sesiones, apagada por defecto.
18. [ ] Tarea opcional de actualización tras el cierre (D15): solo símbolos con posición abierta, apagada por defecto, con zona horaria explícita, tope por ejecución y pausa entre llamadas. Unit: apagada no hace nada; símbolo repetido entre usuarios se procesa una vez; fin de semana y festivo no llaman; un fallo no detiene el resto.
