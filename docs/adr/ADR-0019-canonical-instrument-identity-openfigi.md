# ADR-0019: Identidad canónica de instrumentos vía OpenFIGI (security master) — `share_class_figi` como llave de unificación cross-market y hub de mapeo de proveedores

**Status:** Accepted
**Date:** 2026-10-26
**Deciders:** Aristeo (owner)
**Módulo:** `asset` (catálogo / security master); habilita `marketdata` (mapeo de proveedores), `transaction`/`tax`
**Relacionado:** [[ADR-0008]] (catálogo single-source-of-truth), [[ADR-0014]] (corporate actions / sucesión — ya reservó estas columnas), [[ADR-0016]] (proveedores de precio multi-mercado), [[ADR-0017]] (cálculo fiscal por régimen), [[ADR-0018]] (histórico inmutable + reconciliación), [[ADR-0002]] (CredentialsIT).

## Context

El hilo completo de proveedores (precios ADR-0016, dividendos ADR-0018, fiscal ADR-0017) choca contra un problema de raíz: **el ticker es ambiguo e inestable**.
- **Ambiguo cross-market:** MELI es `MELI` en NASDAQ (USD) y `MELIN` en BMV/SIC (MXN) — el mismo instrumento económico, dos símbolos.
- **Inestable:** WETO cambió con el reverse split; GOOGL vs GOOG (clases); renames, de-SPACs, mergers.
- Cada proveedor tiene su propia convención (`MELI.US`/`MELIN.MX` en EODHD, `emisora_serie=MELIN`+`bolsa=BMV` en databursatil, ticker en Massive/Alpha Vantage).

**El esquema ya anticipó la solución.** `asset_catalog` ([[ADR-0008]]/[[ADR-0014]]) **ya tiene** las columnas del security master, pero **sin poblar** (no hay integración que las llene):

```java
// composite_figi = futura llave canónica; share_class_figi une cross-border; cik = emisor SEC; isin opcional. NO CUSIP (licenciado).
composite_figi, share_class_figi, cik, isin, predecessor_symbol, predecessor_figi
```

**OpenFIGI** (respaldado por Bloomberg) es exactamente el poblador faltante: mapea `TICKER`/`ISIN`/`SEDOL` → **FIGI** y da metadata estática (name, `securityType`, `exchCode`, `marketSector`, `compositeFIGI`, `shareClassFIGI`). No trae precios, dividendos ni corporate actions — es **solo identidad**, y gratuito.

## Decision

Adoptar **OpenFIGI como poblador del security master** y **promover `share_class_figi` a llave canónica** de unificación cross-market y hub de mapeo de proveedores.

1. **`OpenFigiAdapter`** → `POST /v3/mapping` (header `X-OPENFIGI-APIKEY` por `@Value` desde `application.yml`, nunca hardcoded; requests en **lote** —array de queries— para respetar rate-limit). Entrada típica: `[{"idType":"TICKER","idValue":"MELI","exchCode":"US"}]`.
2. **Poblar** `composite_figi`, `share_class_figi`, `isin`, `cik` en las filas del catálogo: **backfill una vez** + al alta de activos nuevos. Son **datos estáticos**: el FIGI de un instrumento no cambia → mismo principio inmutable de [[ADR-0018]] (fetch-once, caché casi infinita). **No** se guarda CUSIP (licenciado).
3. **`share_class_figi` = llave canónica cross-market.** `asset_catalog` mantiene `symbol` como PK (una fila por listing); las filas del **mismo instrumento económico** comparten `share_class_figi` (MELI NASDAQ y MELIN BMV → mismo `shareClassFIGI`). Indexar por `share_class_figi` para agrupar listings.
4. **Hub de mapeo de proveedores:** la llave canónica resuelve a la convención de cada proveedor — `share_class_figi` → { EODHD `MELI.US`/`MELIN.MX`, databursatil `MELIN`+BMV, Massive ticker, Alpha Vantage }. Habilita el ruteo por mercado de [[ADR-0016]].
5. **Consumo aguas abajo:** [[ADR-0018]] empareja dividendos por FIGI canónico (no ticker); [[ADR-0017]] usa `isin` (país emisor = 2 primeras letras) y `securityType` como pistas de régimen; [[ADR-0014]] ya usa `predecessor_figi` para sucesión.

## Options Considered

### Option A: OpenFIGI security master + `share_class_figi` canónica — **recomendada**
| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja-media (columnas ya existen; solo poblador + índice) |
| Costo | **Gratis** (OpenFIGI); con API key, mejor rate-limit |
| Estabilidad | FIGI inmutable; une cross-border por diseño |
| Habilita | ADR-0016/0017/0018 (mapeo, régimen, reconciliación) |

**Pros:** resuelve ambigüedad e inestabilidad de ticker; infra ya prevista; dato estático cacheable. **Cons:** una integración más (mitigada: identidad, baja frecuencia).

### Option B: Seguir con el ticker como llave (status quo)
**Pros:** cero trabajo. **Cons:** MELI≠MELIN no se unifican; WETO/GOOGL rompen el histórico; cada proveedor exige mapeo ad-hoc disperso. No escala con multi-mercado.

### Option C: ISIN como llave primaria
**Pros:** estándar conocido. **Cons:** el ISIN es a nivel security (no a nivel share-class global como `shareClassFIGI`), su disponibilidad/licenciamiento varía, y OpenFIGI de todos modos es el puente para obtenerlo. Mejor **ISIN como atributo** (pistas de país/fiscal), no como PK.

## Trade-off Analysis

El trabajo real es mínimo porque [[ADR-0008]]/[[ADR-0014]] **ya reservaron las columnas** y el comentario del código ya nombra `composite_figi` como "futura llave canónica": esto es poblar + indexar + un adapter, no un rediseño. El valor es desproporcionado: una **sola llave estable** (`share_class_figi`) convierte el mapeo de proveedores, la reconciliación de dividendos y el ruteo fiscal de *N soluciones ad-hoc por ticker* a *una resolución canónica*. Option B no escala al multi-mercado que ya tenemos (MELI/MELIN); Option C es un subconjunto peor (ISIN se obtiene vía OpenFIGI igual).

## Consequences

**Más fácil:**
- Unificar listings del mismo instrumento (MELI.US ↔ MELIN.MX) como una posición.
- Mapeo proveedor↔símbolo centralizado por llave canónica ([[ADR-0016]]).
- Reconciliación de dividendos/corporate actions por FIGI estable, no por ticker ([[ADR-0018]]).
- Pistas de régimen fiscal por `isin`/`securityType` ([[ADR-0017]]).

**Más difícil / a vigilar:**
- Rate-limit de OpenFIGI: usar **lotes** y caché; el backfill respeta el límite.
- Instrumentos sin FIGI claro (cripto no aplica; algunos SIC/locales pueden faltar) → fallback a ticker + marcar `figi_status`.
- Elegir `exchCode` correcto en el mapeo para no traer el listing equivocado.

**A revisitar:**
- Si en el futuro se quiere PK por FIGI en vez de `symbol` (cambio mayor; hoy no se necesita).

## Action Items

### Fase 1 — Poblador OpenFIGI
1. [ ] `OpenFigiProperties` (`base-url`, `api-key`) por `@Value`/`@ConfigurationProperties`; sin hardcode.
2. [ ] `OpenFigiAdapter` (`POST /v3/mapping`, header `X-OPENFIGI-APIKEY`, **batch**; mapear rate-limit a excepción recuperable).
3. [ ] `OpenFigiCredentialsIT` en `com.mx.cryptomonitor.integration.marketdata` ([[ADR-0002]]): mapeo real `TICKER:IBM,US` → `figi/shareClassFIGI` + contrato JSON.
4. [ ] Backfill de `composite_figi`/`share_class_figi`/`isin`/`cik` en `asset_catalog` (una vez) + resolución al alta de activos nuevos; `figi_status` (RESOLVED/NONE/PENDING) como en el patrón de logo.

### Fase 2 — Llave canónica y mapeo de proveedores
5. [ ] Índice por `share_class_figi`; consultas que agrupan listings del mismo instrumento.
6. [ ] Mapa canónico `share_class_figi` → símbolo por proveedor (EODHD/databursatil/Massive/Alpha Vantage), consumido por [[ADR-0016]].
7. [ ] Conectar reconciliación de dividendos ([[ADR-0018]]) y ruteo fiscal ([[ADR-0017]]) a la llave canónica.
