# ADR-0008: El catálogo es la única fuente de verdad de metadatos de activo, con resolución por-campo

**Status:** Accepted (implementado 2026-09-20)
**Date:** 2026-09-20
**Deciders:** Aristeo (owner backend)
**Módulo:** `asset` (application.service.CatalogSyncService, outbound.redis, domain.model), `transaction` (application.service.TransactionService, mapper, domain.model)
**Relacionado:** [[ADR-0007]]

## Context

`GET /api/v1/me/transactions` devuelve el ticker como nombre en vez de la razón social.
Caso observado (cuenta de prueba `sectest_f19zgoyd@example.com`):

```
WETO (STOCK) -> assetName = "WETO"                    <- ticker, no el emisor
GOOG (STOCK) -> assetName = "Alphabet Inc. Class C"   <- correcto (sync FMP)
BNB  (CRYPTO)-> assetName = "BNB"                      <- nombre real (coincide con ticker)
```

Finnhub **sí** devuelve el nombre real:

```
GET /stock/profile2?symbol=WETO
{ "ticker":"WETO", "name":"Wetour Robotics Ltd", "currency":"CNY",
  "exchange":"NASDAQ NMS - GLOBAL MARKET", "logo":"https://static2.finnhub.io/.../950838209996.png", ... }
```

No es un dato suelto: es una **clase de error — "enriquecimiento parcial del proveedor"** con
dos causas de diseño:

1. **Placeholder persistido como autoritativo.** En el catalogado on-demand, para un símbolo
   nuevo se siembra `name = símbolo` (`CatalogSyncService.resolveAndCacheOne`,
   `new AssetCatalogDto(upper, upper, ...)`). `enrichStock` luego trae del `StockProfile`
   **solo `logoUrl` + `marketCap`** e ignora `name`, `exchange`, `currency`, aunque el DTO ya
   los contiene (`StockProfilePort.StockProfile` tiene los cinco campos). La fila se marca
   `logo_status=RESOLVED` y el placeholder queda "resuelto" para siempre; el retry
   (`fallbackRetryDue`) solo revisa el logo. Por eso: logo real, nombre = ticker. El mismo
   defecto pierde `currency` (WETO es `CNY`) y `exchange` (NASDAQ).

2. **Metadatos de display denormalizados y congelados.** `assetName` se persiste en
   `transaction.asset_name` en el alta y se devuelve verbatim (`TransactionMapper.toResponse`),
   mientras el `logoUrl` **sí** se re-resuelve en cada lectura (`toResponsesWithLogos`). Dos
   fuentes de verdad que divergen: aunque se corrija el catálogo, las transacciones ya creadas
   siguen mostrando el ticker.

Restricción dura: Finnhub free = 60 req/min; el hot path de lectura **no** debe llamar al
proveedor. Se descartan explícitamente soluciones temporales (heurísticas `name==symbol`,
auto-upgrade dependiente del tráfico): esta ADR ataca la raíz.

## Decision

Hacer del **catálogo la única fuente de verdad** de los metadatos de display (name/logo/
exchange/currency), con **estado de resolución por campo**, y **des-denormalizar** el nombre de
la transacción.

1. **Nunca persistir un placeholder de nombre.** La base de un símbolo no resuelto usa
   `name = null` (no el símbolo). `name == null` significa inequívocamente "aún no resuelto".
   Mostrar el ticker cuando no hay nombre es una **decisión de render del frontend**, nunca un
   dato guardado.

2. **Estado de resolución por-campo** (migración Flyway), en vez del único `logo_status`:
   ```
   name_status     ENUM(RESOLVED, PENDING, NONE)
   name_checked_at timestamptz
   logo_status     ENUM(RESOLVED, FALLBACK, NONE)   (existente)
   ```
   - `RESOLVED` = valor autoritativo del proveedor → **nunca se pisa**.
   - `PENDING` = el proveedor aún no lo dio → elegible para reconciliación.
   - `NONE` = cache de negativos (el proveedor no lo tiene) → no reintentar.
   Generaliza a `currency`/`exchange` sin rediseñar.

3. **Enriquecimiento como unidad, guardado por campo.** `enrichStock` (y
   `resolveStockLogoOnDemand`) mapean **todos** los campos autoritativos del `StockProfile`,
   cada uno con su estado. Regla `resolveField`: si ya está `RESOLVED` se conserva; si el
   proveedor da valor no vacío → `RESOLVED`; si respondió sin dato → `NONE`; si no respondió →
   `PENDING`. Idempotente: un reintento nunca degrada un valor bueno.

4. **Catálogo = verdad de display; des-denormalizar la transacción.** Eliminar
   `transaction.asset_name` (o renombrarlo a `broker_reported_name` si se decide conservar el
   nombre reportado por el broker en el alta, con semántica propia y separada). La lectura
   resuelve name+logo juntos, en batch, desde el catálogo (nuevo
   `findDisplayBySymbols(symbols) -> {name, logoUrl}` que reemplaza a `findLogosBySymbols`).
   Consecuencia: **WETO y todo el histórico se corrigen sin backfill de la tabla
   `transaction`**, porque deja de haber dos copias.

5. **Reconciliación determinista** (no "al acceder"). Un paso en el job programado existente
   (`syncDailyRanking`, 06:00) resuelve los `name_status=PENDING` con el throttle de Finnhub
   (1.1 s) y backoff vía `name_checked_at`. La corrección no depende del tráfico de usuarios.

6. **Gate de calidad al catalogar.** Aplicar al pull-once el mismo criterio que ya existe para
   IPOs (`qualifiesForCatalog`): coherencia currency/exchange y umbral mínimo, para no meter
   micro-listados a medio resolver en "Seleccionar Activo". (Toca reglas de negocio; se entrega
   como paso separado.)

## Options Considered

### Option A: Catálogo única fuente + estado por-campo + des-denormalizar (elegida)
**Pros:** elimina la clase de error de raíz; el histórico queda consistente sin backfill de
datos transaccionales; sin llamadas al proveedor en lectura; el modelo por-campo absorbe
futuros campos (sector, país, ISIN). **Cons:** migración de esquema y cambio de contrato con el
frontend (mostrar ticker cuando `assetName==null`).

### Option B: Heurística `name==symbol` + auto-upgrade al acceder
**Pros:** cero migración, arreglo inmediato. **Cons:** implícito y frágil (falsos
"provisionales"), la corrección depende del tráfico, y **no resuelve la divergencia estructural**
transacción↔catálogo. **Rechazada por ser solución temporal.**

### Option C: Backfill de `transaction.asset_name` dejando la denormalización
**Pros:** no toca el contrato de lectura. **Cons:** repara el pasado pero **deja viva la causa**:
la próxima transacción con un símbolo nuevo vuelve a congelar el ticker; dos fuentes de verdad
siguen coexistiendo. **Rechazada.**

## Consequences

- **Más fácil:** un solo lugar (catálogo) define name/logo; corregir un nombre corrige todas las
  vistas (portfolio, transacciones, buscador) a la vez; el frontend no inventa datos.
- **Más difícil:** hay que coordinar migración Flyway + ajuste del read path de `transaction` +
  contrato con el frontend (`assetName` puede venir `null` → render del símbolo).
- **A revisar:** con más proveedores/campos, extraer un `enrichX` genérico sobre el modelo
  por-campo; y consolidar/documentar cuál base y Redis usa la app corriendo (durante el análisis
  se observó divergencia entre stores locales nativa vs Docker en `localhost`, fuera del alcance
  de esta ADR pero relevante para operar y verificar).

## Implementación (2026-09-20) — desviaciones conscientes respecto al plan

Al implementar se simplificaron dos decisiones sin perder la propiedad definitiva:

- **Decision 2 (estado por-campo):** en vez de una columna `name_status`, se usó el **sentinel
  `name IS NULL` = "no resuelto"** (posible al eliminar el placeholder). La reconciliación diaria
  rellena los NULL; no hace falta un enum nuevo. Si más adelante `currency`/`exchange` necesitan
  el mismo tratamiento con cache de negativos, se introduce `name_status` entonces.
- **Decision 4 (des-denormalizar):** `transaction.asset_name` **no se eliminó**; se conserva como
  **snapshot de respaldo** (el nombre reportado por el broker en el alta). El read path prefiere el
  catálogo y solo cae al snapshot si el catálogo aún no tiene nombre. Se evita un DROP irreversible
  y el histórico igual se corrige vía catálogo. (Renombrar a `broker_reported_name` queda opcional.)
- **`exchange`** no se rellena en `enrichStock` por el límite `VARCHAR(20)` de la columna (Finnhub
  devuelve p.ej. "NASDAQ NMS - GLOBAL MARKET", 26 chars). Requiere ensanchar la columna → follow-up.
- **CRYPTO on-demand:** sin proveedor de nombre, el `name` queda `null` (antes: símbolo). El front
  muestra el símbolo. Sin regresión visual.

## Action Items
1. [x] Flyway `V2026_09_20_01`: `name` NULLABLE + one-shot que anula placeholders `name = symbol`
   (solo STOCK).
2. [x] `resolveAndCacheOne`: base con `name=null` (sin placeholder de símbolo).
3. [x] `enrichStock`: copia `name` (y `currency`) del `StockProfile` con `firstNonBlank`; nunca pisa
   un nombre bueno. (`exchange` diferido por límite de columna.)
4. [x] `findDisplayBySymbols` en `AssetCatalogQueryPort`/`AssetSearchService`; `TransactionService`
   resuelve name+logo desde el catálogo en lectura (con fallback al snapshot de la transacción).
5. [x] Reconciliación en `syncDailyRanking`: rellena `name`/`currency` NULL desde Finnhub (throttle
   existente) y sincroniza Redis.
6. [x] One-shot de backfill incluido en la migración (anula placeholders → reconciliación los llena).
7. [x] Tests: unit (asset + transaction) e integración (`TransactionCreateWithBearerIT`,
   `AssetNameResolutionIT`, `BuyTransactionNewFieldsIT`, `AssetSearchEndpointIT`) en verde.
8. [ ] (Separado) Gate de calidad al catalogar (`qualifiesForCatalog` en pull-once).
9. [ ] Frontend: mostrar el símbolo cuando `assetName == null`.
10. [ ] (Follow-up) Ensanchar `asset_catalog.exchange` y rellenar `exchange` desde el proveedor.
