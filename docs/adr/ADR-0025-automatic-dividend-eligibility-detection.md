# ADR-0025: Detección automática de elegibilidad de dividendos por activo

**Status:** Accepted
**Date:** 2026-11-06
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (descubrimiento + estado persistido), consume el disparo de `transaction` (ADR-0024); el catálogo de `asset` aporta `currency`/`exchange` para el ruteo
**Relacionado:** [[ADR-0024]] (auto-sync al registrar compra), [[ADR-0022]] (mapeo de símbolos/ACL por proveedor), [[ADR-0020]] (historia de dividendos inmutable), [[ADR-0014]] (corporate actions framework)

## Context

**Requisito (caso real BIMBO A):** el usuario captura **solo** datos de la operación — emisora/símbolo, tipo (Compra), títulos, precio, comisiones, fecha. **No existe** —ni debe existir— un botón "este activo paga dividendos". El **sistema** debe determinarlo solo.

ADR-0024 ya cubre el **disparo**: al registrar una compra de STOCK/ETF, pregunta a los proveedores y puebla el historial. Eso **ya es** detección empírica (sin intervención del usuario). Pero como mecanismo de *detección robusto*, ADR-0024 tiene **4 huecos**:

1. **No distingue "no paga" de "no se sabe".** La única señal es *¿hay filas en `corporate_action`?*. Si el proveedor estaba **caído o sin cuota** (AlphaVantage 25/día), devuelve vacío → el activo se vería como **"no paga"** cuando en realidad **no se verificó**. Marcar a un pagador real como no-pagador por un fallo transitorio es el riesgo #1.
2. **Ruteo ciego por mercado.** Para BIMBOA (BMV/MXN), el orquestador prueba **primero los proveedores US** (Massive→FMP→AlphaVantage, todos vacío) y recién después DataBursatil. Funciona, pero **quema cuota** y latencia. El catálogo ya sabe `currency=MXN`/`exchange=BMV` → se puede rutear directo.
3. **Resolución de símbolo.** "BIMBO A" (emisora + serie) debe mapear a **BIMBOA** y al proveedor correcto. Un mapeo errado = falso "no paga". Territorio de [[ADR-0022]].
4. **Una sola verificación.** Un activo que hoy no paga puede **empezar** (GOOGL empezó en 2024). Un one-shot al registrar no re-verifica nunca.

**Fuerza clave:** "detección" tiene dos lecturas — (a) *clasificar a priori* (un flag/lista mantenida) y (b) *descubrir a posteriori* (preguntar al proveedor). El repo ya eligió (b). Lo que falta no es un clasificador manual, sino **persistir el resultado del descubrimiento como un estado de primera clase** con semántica clara.

## Decision

**Persistir un `dividend_status` por símbolo, alimentado por el descubrimiento de ADR-0024 + un re-chequeo periódico, con ruteo por mercado/moneda.** La detección sigue siendo empírica (sin lista a mano, sin botón), pero su **resultado** se vuelve un dato consultable con estados explícitos:

| Estado | Significado |
|---|---|
| `UNKNOWN` | aún no verificado (símbolo nuevo; el descubrimiento async aún no corre) |
| `PAYS` | el proveedor devolvió pagos → paga (hay historial poblado) |
| `NONE` | el proveedor respondió **OK y vacío** → confirmado que no paga |
| `ERROR` | el proveedor falló/sin cuota → **no concluyente**, re-intentar (NO es `NONE`) |

`lastCheckedAt` + `checkedProvider` acompañan el estado. El `sync` ya no solo inserta filas: **reporta el desenlace** (OK-con-datos / OK-vacío / fallo) para fijar el estado. El ruteo del orquestador se ordena por `currency`/`exchange` del catálogo (MXN/BMV → DataBursatil primero; USD → Massive→FMP→AlphaVantage).

## Options Considered

### Option A: Descubrimiento on-demand puro, sin estado persistido (status quo, ADR-0024)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja (ya hecho) |
| Costo | Bajo |
| Escalabilidad | Media |
| Robustez | **Baja** |

**Pros:** cero trabajo extra; ya funciona para el camino feliz.
**Cons:** no distingue `NONE` de `ERROR`/`UNKNOWN` → falsos "no paga" por fallos de proveedor; sin re-chequeo; ruteo ciego; la UI no puede mostrar un badge sin re-consultar.

### Option B: Estado de dividendos persistido, alimentado por descubrimiento + re-chequeo (ELEGIDA)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media |
| Costo | Bajo (reusa ADR-0024) |
| Escalabilidad | Buena |
| Robustez | **Alta** |

**Pros:** "el sistema ya lo sabe" de verdad — tras el primer descubrimiento queda `PAYS`/`NONE` consultable al instante, sin botón ni re-query; separa `ERROR` de `NONE` (no más falsos negativos); re-chequeo periódico atrapa nuevos pagadores; ruteo por mercado ahorra cuota.
**Cons:** añade una tabla de estado y un job de re-chequeo; símbolo nuevo arranca en `UNKNOWN` unos segundos hasta que el async termina.

### Option C: Screener/pre-clasificación de todo el catálogo

| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta |
| Costo | **Alto** (barrido masivo, cuota) |
| Escalabilidad | Buena una vez hecho |
| Robustez | Alta |

**Pros:** el catálogo entero queda pre-marcado; detección instantánea incluso antes de registrar.
**Cons:** caro y con sesgo US (los *dividend-list*/screener free cubren mal BMV; FMP screener da 402 en el plan actual); mantener fresco todo el catálogo es desperdicio para activos que nadie tiene. Complemento futuro, no base.

## Trade-off Analysis

El eje es **"¿basta con descubrir, o hay que recordar el desenlace?"**. Option A descubre pero **olvida el porqué del vacío**, que es justo donde se cuela el bug (proveedor caído ≡ "no paga"). Option B convierte el desenlace del descubrimiento en un **estado con memoria y semántica** — el mínimo que hace que "el sistema ya lo sabe" sea cierto y confiable, reusando todo ADR-0024. Option C ataca por fuerza bruta y choca con los límites reales de los proveedores free + BMV; se reserva como optimización posterior para símbolos "populares". Rutear por `currency`/`exchange` (ya en el catálogo) es una mejora transversal barata que aplica con A o B.

## Consequences

- **Más fácil:** la UI muestra "Paga dividendos / No paga / Verificando…" sin que el usuario marque nada ni se re-consulte al proveedor en cada vista.
- **Más fácil:** desaparecen los falsos "no paga" por fallos transitorios; `ERROR` se reintenta.
- **Más difícil / a vigilar:** definir la **cadencia de re-chequeo** (`NONE` cada ~30 días, `ERROR` backoff corto, `PAYS` al ritmo del calendario) y mantener el ruteo símbolo→proveedor.
- **A revisar:** resolución símbolo→proveedor para BMV (serie/emisora) se vuelve crítica; apoyarse en [[ADR-0022]].

## Fases de implementación

- **Fase 1 — Estado persistido (`UNKNOWN`/`PAYS`/`NONE`):** tabla `asset_dividend_status` + enum + repo + migración Flyway; `DividendSyncService` fija el estado tras cada sync (PAYS si el proveedor devolvió eventos, NONE si vacío). `ERROR` se difiere a la Fase 2. Sin cambio de contrato.
- **Fase 2 — `ERROR` vs `NONE` (robustez) [HECHA]:** los adapters reales (Massive, FMP, AlphaVantage, DataBursatil) lanzan `DividendProviderUnavailableException` en su ruta de fallo (antes lo tragaban a vacío); AlphaVantage también ante el envelope de cuota/error. EODHD queda NEUTRAL (sigue devolviendo vacío) porque su 403 es una limitación esperada de su plan, no un fallo transitorio, y lanzaría falsos ERROR en símbolos US que no pagan. El orquestador agrega: devuelve el primer no-vacío; si nadie trajo datos pero al menos uno falló, relanza. `DividendSyncService` traduce a `ERROR`, con regla anti-degradación (un ERROR nunca sobre-escribe un PAYS/NONE ya concluyente).
- **Fase 3 — Ruteo por mercado/moneda [HECHA]:** `AssetCatalogQueryPort.findCurrencyBySymbol` (lee del catálogo cacheado) expone la moneda; el orquestador elige el orden: `MXN` → DataBursatil primero (US de respaldo, EODHD último); cualquier otra o desconocida → orden US por defecto. Nuevo edge acíclico `marketdata→asset` (validado por Modulith).
- **Fase 4 — Re-chequeo programado + exponer en contrato:** job `@Scheduled` con cadencia por estado + badge "paga/—/verificando" para el front.

## Action Items

1. [x] (F1) Tabla/entidad `asset_dividend_status(symbol, status, last_checked_at, provider)` + repo + migración.
2. [x] (F1) `DividendSyncService` fija `PAYS`/`NONE` tras cada sync; símbolo nunca visto = `UNKNOWN` (sin fila).
3. [x] (F2) Adapters/orquestador señalan fallo → sync marca `ERROR` (no degradar a `NONE`). EODHD neutral (403 esperado).
4. [x] (F3) Ruteo del orquestador por `currency` del catálogo (MXN→DataBursatil primero).
5. [ ] (F4) Job `@Scheduled` de re-chequeo con cadencia por estado.
6. [ ] (F4) Exponer el estado en el contrato que consume el front (badge).
