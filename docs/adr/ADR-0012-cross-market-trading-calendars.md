# ADR-0012: Calendarios bursátiles cruzados (NYSE/NASDAQ vs BMV) y refresco de precios por mercado

**Status:** Accepted
**Date:** 2026-10-07
**Decisiones del owner:** `/api/v1/market/status` **público** (sin auth, para landing) con `Cache-Control`
y rate limiting; un solo endpoint con ambos mercados; `nextChange` calculado en backend; motor computado
como fuente de verdad. Arranca **F1**.
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (calendario, schedulers, snapshots)
**Relacionado:** [[ADR-0011]] (sweep `@Scheduled`), [[ADR-0009]] (valuación por fecha), [[ADR-0006]] (FX/moneda)

## Context

El sistema maneja activos de **dos mercados con calendarios independientes**:

- **USA (NYSE/NASDAQ):** zona `America/New_York`, sesión 09:30–16:00. Festivos propios donde EE.UU.
  cierra pero México abre (Memorial Day, 4 de Julio, Thanksgiving, etc.).
- **México (BMV):** zona `America/Mexico_City`, sesión 08:30–15:00. Días donde México cierra pero EE.UU.
  opera (16 de Septiembre, 20 de Noviembre, etc.).

El software **no puede asumir** que "si es día hábil en México hay precios nuevos en USA" (ni viceversa).

**Estado actual (gaps):**
1. La clasificación de mercado es por **moneda/exchange** (`HybridQuoteService`: MXN→DataBursatil/BMV,
   USD→orquestador), no por calendario.
2. Hay **un solo** refresco programado: `HybridQuoteService.scheduledRefresh()` refresca **solo MXN/BMV**,
   `cron = 0 */5 9-15 * * MON-FRI`, **sin zona horaria explícita y sin saltar festivos**. Los activos
   **USD no tienen refresco programado** (se piden on-demand).
3. **No existe ningún calendario de festivos** de bolsa (ni NYSE ni BMV) en el código ni en la DB.
4. Crypto opera 24/7 y no tiene calendario.

## Decision

1. **Abstracción `Market`** (enum de dominio): `US_EQUITY` (`America/New_York`, 09:30–16:00),
   `MX_BMV` (`America/Mexico_City`, 08:30–15:00). Crypto queda **fuera** del calendario (24/7).
2. **Motor primario = calendario COMPUTADO** (determinista, offline, fuente de verdad):
   `ZoneId` por mercado (DST automático; `America/Mexico_City` ya es CST fijo tras la abolición del
   horario de verano en MX) + horarios de sesión + **tabla `market_holiday(market, holiday_date, name,
   early_close TIME NULL)`** con `UNIQUE(market, holiday_date)`, sembrada por **Flyway** (calendarios
   OFICIALES NYSE y BMV/BIVA, verificados — ver Appendix). El **US puede además auto-extenderse** desde un
   proveedor (F4) para no migrar cada año; **MX/BMV se mantiene por migración** (9 fechas estables, sin
   proveedor gratis). Esto responde **ambas** preguntas: "¿abierto ahora?" (fase actual) y "¿fue/será hábil
   la fecha D?" (valuación/histórico/próximo cambio). El backend es la autoridad; nada de festivos vive en
   el bundle del frontend.
3. **Live market-status API = OPCIONAL (cross-check / auto-detección), no la fuente del gate:** el "now"
   de **Massive `/v1/marketstatus/now`** (US; **no cubre BMV**) y **Alpha Vantage `MARKET_STATUS`** (incluye
   Mexico; rate limits 5/min·25/día) sirve para **detectar festivos no sembrados** y cross-chequear la
   tabla (log/alerta si difieren), no para cada decisión. Se evita depender de él por cobertura (Massive
   sin BMV) y rate limits. El sistema funciona 100% con el calendario computado si el live falla.
4. **`TradingCalendarService`** (dominio, cacheable): `statusAt(market, instant)` → fase
   (`OPEN|PRE_MARKET|AFTER_HOURS|CLOSED`) + `reasonCode` + `nextChange`; `isOpenNow`, `isTradingDay(date)`,
   `nextOpen` derivados. Todo computado del calendario (punto 2). Caché en memoria/Redis.
5. **Endpoint de consumo (contrato con frontend): `GET /api/v1/market/status`** — devuelve ambos mercados
   en un array, computado del calendario, `Cache-Control: max-age=30`. Por mercado: `code`, `label`,
   `exchange`, `timezone`, `phase`, `isOpen`, `regular{open,close}` y `extended{pre,after}` en **HH:mm
   local**, `nextChange{type,at}` con **`at` en instante UTC ISO-8601**, `reasonCode`. BMV: `extended=null`
   y fase solo `OPEN|CLOSED`; NYSE: las 4 fases + extended + early-close. **Auth:** `ROLE_USER` en F1;
   público para landing = fast-follow (matcher `permitAll` + rate limiter + sign-off del owner). Fase 2:
   `GET /api/v1/market/{code}/calendar?year=` (festivos + cierres anticipados).
   Enums — `phase`: `OPEN|PRE_MARKET|AFTER_HOURS|CLOSED`; `reasonCode`:
   `REGULAR|EARLY_CLOSE|PRE_MARKET|AFTER_HOURS|BEFORE_OPEN|AFTER_CLOSE|WEEKEND|HOLIDAY`.
6. **Routing activo → mercado:** reusar la clasificación existente (`currency`/`exchange`):
   MXN o `exchange=BMV` → `MX_BMV`; USD → `US_EQUITY`. Sin columna nueva en `transaction`.
7. **Schedulers independientes por mercado** (Spring `@Scheduled` soporta `zone`):
   - `refreshMxQuotes` (el actual): + `zone="America/Mexico_City"` + gate
     `if (!cal.isOpenNow(MX_BMV, now)) return;` (computado del calendario).
   - **`refreshUsQuotes` (nuevo):** `zone="America/New_York"`, ventana de sesión NYSE, gate
     `if (!cal.isOpenNow(US_EQUITY, now)) return;`; refresca snapshots de los activos **USD** de los
     portafolios (espejo del refresco MXN, vía el orquestador de proveedores USD). Cada job **salta su
     propio cierre** ⇒ "USA cerrado el 4-jul pero BMV abre" y "México cerrado el 16-sep pero USA opera"
     quedan resueltos por separado.
   - Cada job con su flag `enabled` para desactivarlo en tests (patrón `scheduledRefreshEnabled`).
8. **El gate nunca rompe el flujo on-demand:** las cotizaciones puntuales (al abrir un activo) siguen
   disponibles; el calendario solo gobierna el **refresco programado** y, opcionalmente, qué cierre se
   usa en valuación (fase posterior).

## Options Considered (resumen)

- **Un solo cron frecuente que valúa ambos mercados:** rechazada — no respeta zonas ni festivos; corre en
  vano la mitad de los días festivos cruzados.
- **Clasificar por país con columna nueva en `transaction`:** innecesario — `currency`/`exchange` ya
  determinan el mercado; evita migración de datos.
- **Solo live market-status API para todo:** rechazada — el endpoint "now" (AV `MARKET_STATUS`, Massive
  `/marketstatus/now`) responde "¿abierto ahora?" pero **no** "¿fue hábil la fecha D?" (valuación/
  histórico); además Massive **no cubre BMV** y AV tiene rate limits estrictos. No puede ser la única
  fuente.
- **Elegida: calendario COMPUTADO como motor único de verdad** (`Market` + `ZoneId` + horarios + tabla
  `market_holiday`/early-close) que sirve **el endpoint `/market/status` rico Y el gate del scheduler Y las
  preguntas por fecha**, de forma determinista y offline. El **live status (Massive/AV) queda opcional**
  (auto-detección de festivos no sembrados + cross-check), no en el camino crítico — así se evita la falta
  de cobertura BMV de Massive y los rate limits de AV, y el sistema no depende de terceros para funcionar.

## Consequences

- El refresco de precios deja de asumir un calendario único; USA gana refresco programado propio.
- Nueva tabla `market_holiday` + mantenimiento anual (una migración por año con los festivos oficiales).
  La tabla permite operar **100% offline** si ambos proveedores de status fallan.
- El live market-status (Massive=US, AV=MX) es **opcional** (cross-check / auto-detección), fuera del
  camino crítico → sin dependencia dura ni presión de rate limits (AV free 5/min·25/día).
- Nuevo endpoint `GET /api/v1/market/status` (consumido por el indicador del frontend); `HybridQuoteService`
  (o un nuevo `MarketQuoteRefreshService`) y el controller ganan dependencia de `TradingCalendarService`.
- **A revisar / fases siguientes:**
  - **Medios días** (early close 13:00 NYSE post-Thanksgiving y Nochebuena; cierres anticipados BMV).
  - **Valuación en día cerrado:** usar el **último cierre** del mercado en lugar de spot cuando
    `!isOpenNow` (conecta con la serie de [[ADR-0009]]).
  - **Fuente de festivos automática** (proveedor/API) para no depender de migraciones anuales.
  - Exponer el estado "mercado abierto/cerrado" al frontend (badge en el dashboard).

## Action Items (Fase 1 — calendario + endpoint de estado, sin schedulers)

1. [ ] Enum `Market` (zona + horario regular + extended por mercado) en dominio `marketdata`.
2. [ ] Tabla `market_holiday(market, holiday_date, name, early_close TIME NULL, UNIQUE(market,
       holiday_date))` — Flyway, con seed NYSE + BMV del año actual y el próximo (festivos oficiales
       **verificados** contra BMV/BIVA y NYSE; incl. early-close NYSE 27-nov y 24-dic).
3. [ ] `MarketHolidayEntity` + repositorio + `TradingCalendarService`: `statusAt(market, instant)` →
       `{phase, reasonCode, nextChange}`; `isOpenNow` / `isTradingDay(date)` / `nextOpen` derivados. Todo
       computado (zona + horario + tabla), cacheado.
4. [ ] Routing `currency`/`exchange` → `Market` (helper reutilizable).
5. [ ] `GET /api/v1/market/status` (controller `marketdata`): ambos mercados, `Cache-Control: max-age=30`,
       `ROLE_USER`. Shape del contrato con frontend (HH:mm local + `nextChange.at` UTC).
6. [ ] Tests: festivos cruzados (4-jul USA cerrado / BMV abierto; 16-sep BMV cerrado / USA abierto),
       fin de semana, pre/after NYSE, early-close, `statusAt` por zona; slice `@WebMvcTest` del endpoint.

### Fases siguientes

- **F2:** `refreshUsQuotes` (`@Scheduled` + `zone`) + gate `isOpenNow` en ambos crons + flags `enabled`;
  `GET /api/v1/market/{code}/calendar?year=` (festivos + early-close). Endpoint público opcional
  (permitAll + rate limiter) con sign-off del owner.
- **F3:** valuación con último cierre en mercado cerrado (conecta con [[ADR-0009]]).
- **F4 (opcional) — ingesta automática del calendario (reduce el seed anual):**
  - **US:** `MarketHolidayProviderPort` + adapter **Massive `/v1/marketstatus/upcoming`** (primario — ya
    integrado; da NYSE/NASDAQ, `status closed|early-close` y `open`/`close` en **instantes UTC**, sin
    parseo de hora local), con **Finnhub `/stock/market-holiday`** o **FMP `/holidays-by-exchange`** como
    **backfill multi-año** (tienen años pasados, para "¿fue hábil tal fecha?" histórico) y cross-check.
    Ingesta tipo sweep (como [[ADR-0011]]) que **upsert** en `market_holiday` ⇒ ya no hace falta migración
    anual para US. `*CredentialsIT` por proveedor.
  - **MX/BMV:** permanece **sembrado por Flyway** (9 fechas estables/año; sin proveedor gratis fiable).
  - Opcional: `/v1/marketstatus/now` (Massive US) + AV `MARKET_STATUS` (MX) para el estado "en vivo" como
    cross-check del cálculo. El badge del frontend ya lo cubre `/market/status`.
  - El **seed de Flyway sigue siendo el baseline offline** para ambos; el proveedor solo extiende/refresca.

## Appendix — Seed de festivos 2026 (verificado vs. reglas oficiales NYSE/BMV)

Fechas verificadas (coinciden con el fallback del frontend). Al sembrar, confirmar contra el PDF oficial
BMV/BIVA y NYSE del año (autoridad), por si hay ajustes discrecionales.

**NYSE/NASDAQ — 10 cierres totales:** 2026-01-01 New Year; 2026-01-19 MLK (3er lun ene);
2026-02-16 Washington (3er lun feb); 2026-04-03 Good Friday; 2026-05-25 Memorial (últ. lun may);
2026-06-19 Juneteenth; 2026-07-03 Independence (observado, 4-jul=sábado); 2026-09-07 Labor (1er lun sep);
2026-11-26 Thanksgiving (4º jue nov); 2026-12-25 Christmas.
**NYSE — early-close 13:00 ET (aftermarket a 17:00):** 2026-11-27 (post-Thanksgiving); 2026-12-24 (Nochebuena).

**BMV/BIVA — 9 cierres totales:** 2026-01-01 Año Nuevo; 2026-02-02 Constitución (1er lun feb, nominal 5-feb);
2026-03-16 B. Juárez (3er lun mar, nominal 21-mar); 2026-04-02 Jueves Santo; 2026-04-03 Viernes Santo;
2026-05-01 Trabajo; 2026-09-16 Independencia; 2026-11-16 Revolución (3er lun nov, nominal 20-nov);
2026-12-25 Navidad.

**Omitidos a propósito (no son cierres bursátiles BMV):** 12-dic Guadalupe (no está en calendario BMV
ningún año) y 02-nov Día de Muertos (no cierra; en 2026 cae lunes — cuidar no incluirlo por error).
