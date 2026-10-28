# ADR-0018: Almacenamiento inmutable de histórico de dividendos/corporate actions — freeze-al-pago, catálogo compartido y reconciliación multi-fuente

**Status:** Accepted
**Date:** 2026-10-26
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (catálogo `corporate_action` + detalle); consumido por `transaction` (accrual por usuario) y el futuro módulo `tax` ([[ADR-0017]])
**Relacionado:** [[ADR-0011]] (splits no-mutantes), [[ADR-0014]] (corporate actions framework, CTI), [[ADR-0017]] (cálculo fiscal — consume esto), [[ADR-0002]] (CredentialsIT), [[project_mxn_costbasis_fx_distortion]] (crudo vs derivado).

## Context

El tax calculator ([[ADR-0017]]) y los reportes de dividendos se apoyan en el histórico de dividendos. Al revisar la naturaleza de ese dato surge un principio de diseño: **un dividendo pagado es un hecho histórico inmutable y perpetuo**. Una vez que pasa el `payment_date`, su monto y fechas **no cambian nunca** — KO tiene dividendos desde el **13-mar-1962** y esa fila será idéntica dentro de décadas.

Hoy el feed ya existe (`MassiveDividendAdapter` → `DividendSyncService` → `corporate_action(DIVIDEND)` + `dividend_action_detail`, idempotente por `provider_event_id`), pero hace **insert idempotente simple**: no distingue hecho congelado de provisional, no reconcilia fuentes y confía en un solo proveedor.

**Evidencia de que confiar en una sola fuente es inseguro (GOOGL, 3 fuentes):** Massive, Yahoo y Alpha Vantage coinciden en los **10 dividendos recurrentes** (0.20→0.22). Pero Alpha Vantage inyecta un **11º registro espurio**:

```
ex_dividend_date: 2014-04-03 · amount: 567.97 · declaration/record/payment_date: "None"
```

Eso **no es un dividendo**: es la **distribución de acciones Clase C (GOOG) de Alphabet de abril-2014** (split 2:1 vía reparto de Clase C); el `$567.97` ≈ el precio de la acción repartida, y las fechas son nulas porque no es efectivo. Si se ingiriera ciegamente: (a) corrompe el **ingreso por dividendos**, (b) corrompe el **ISR** (tributa un ingreso inexistente), y (c) **pierde un split real** que ajusta el costo base ([[ADR-0011]]). Es la prueba de que el catálogo necesita **reconciliación + validación + clasificación**, no solo un insert.

## Decision

Tratar el histórico de dividendos (y por extensión los corporate actions de [[ADR-0014]]) como un **catálogo de referencia inmutable, compartido a nivel símbolo**, con una capa de ingesta que **reconcilia fuentes, valida y clasifica antes de congelar**.

### 1. Ciclo de vida y freeze-al-pago
```
ANNOUNCED ──► EFFECTIVE (pasó ex-date) ──► COMPLETED (pagado) = INMUTABLE
 (revisable)      (revisable)               monto + fechas congelados para siempre
```
- Filas `COMPLETED`: **nunca** UPDATE/DELETE (guard de aplicación + trigger/constraint en DB + auditoría).
- Filas provisionales (`ANNOUNCED`/`EFFECTIVE`): **sí** actualizables (el emisor puede revisar monto/fecha) hasta finalizar; al pasar a `COMPLETED` se congelan.
- El **estado se deriva de las fechas en lectura** (no se fija en el sync-time), para que una fila vieja no quede con estado obsoleto.

### 2. Dónde: catálogo compartido vs accrual por usuario
- **Hecho compartido** → `corporate_action(DIVIDEND)` + `dividend_action_detail`: **una fila por (símbolo, ex-date)**, igual para todos los tenedores. El dividendo de KO de 1962 se guarda **una vez**.
- **Efecto por usuario** (acciones × DPS, retención, ISR) → **derivado**, vive en el accrual (`transaction`/`tax`). No se duplica el hecho; se referencia.

### 3. Crudo vs derivado
- `cash_amount` (efectivo realmente pagado) = **inmutable**, se congela.
- `split_adjusted_cash_amount` / `historical_adjustment_factor` = **derivados y mutables** (se recalculan cuando ocurre un split nuevo). No se tratan como inmutables; se recomputan al leer o al llegar el split ([[ADR-0011]] no-mutante).

### 4. Reconciliación, validación y clasificación (antes de congelar)
- **Procedencia:** `source_provider` + `source_event_id` por fila (ya existe); **fuente canónica por campo**, las demás como verificación cruzada.
- **Validación en ingesta:**
  - Sanidad de monto: cuarentena si se desvía ×N de la cadencia recurrente (ej. $567 vs $0.20) salvo `distribution_type=special`.
  - Fechas críticas nulas en un supuesto dividendo pagado → rechazar.
  - **Clasificación:** reparto de acciones/split → rutear a `SPLIT`/`SPINOFF` ([[ADR-0014]]), **no** a `DIVIDEND`.
- **Cuarentena, no silencio:** divergencias entre fuentes se registran para revisión; no se elige una calladamente.

### 5. Sincronización: backfill profundo + incremental
- **Backfill** histórico completo (décadas) **una vez** por símbolo, al alta en catálogo / primer tenedor.
- **Incremental** después: solo eventos nuevos (tras la última ex-date) y refresco **solo de la cola provisional**; jamás re-descargar la historia congelada.

### 6. Caché y escala
- Pagados → TTL **efectivamente infinito** (como `historicalPrices`, 3650 días); provisionales → TTL corto.
- KO 1962→hoy ≈ **~250 filas**; portafolio entero, miles. **Trivial para Postgres**, sin particionar; el valor es **no re-descargar** lo inmutable.

## Options Considered

### Option A: Catálogo inmutable compartido + reconciliación multi-fuente — **recomendada**
| Dimensión | Evaluación |
|---|---|
| Complejidad | Media (lifecycle + validación + provenance) |
| Correctitud | Alta — atrapa basura como el $567.97 de GOOGL |
| Almacenamiento | Mínimo (una fila por evento, compartida) |
| Base para tax | Sólida ([[ADR-0017]]) |

**Pros:** inmutabilidad real, triangulación de fuentes, clasificación correcta dividend/split, dato compartido. **Cons:** más lógica de ingesta.

### Option B: Insert idempotente de una sola fuente (≈ status quo)
**Pros:** ya existe, simple. **Cons:** confía en un proveedor; el caso GOOGL 2014 **corrompe** ingreso y ISR; no distingue congelado de provisional.

### Option C: Copia de los hechos de dividendo por usuario
**Pros:** autosuficiencia por cuenta. **Cons:** **duplicación masiva** (el dividendo de KO de 1962 replicado por cada tenedor), y N copias que pueden divergir. Contradice "hecho compartido inmutable".

## Trade-off Analysis

La inmutabilidad por sí sola (freeze-al-pago) es barata y de alto valor. El costo real está en la **capa de reconciliación/validación** (Option A vs B): sin ella, un solo proveedor con un registro malo envenena silenciosamente el ingreso y el impuesto —exactamente lo que mostró GOOGL/Alpha Vantage—. Ese costo se paga una vez y protege toda la cadena fiscal aguas abajo, por lo que se justifica. La Option C se descarta por duplicación: el catálogo es referencia compartida, no estado por usuario.

## Consequences

**Más fácil:**
- Histórico de dividendos confiable y auditado, base para [[ADR-0017]] (acreditamiento, FX fiscal a `pay_date`).
- No re-descargar décadas de data estática; caché casi infinita para lo pagado.
- Clasificación correcta dividend vs split/spinoff → costo base consistente ([[ADR-0011]]).

**Más difícil / a vigilar:**
- Guard de inmutabilidad (app + DB) y auditoría de cambios en provisionales.
- Reglas de cuarentena: calibrar el umbral de sanidad de monto para no marcar dividendos especiales legítimos.
- Mantener mapeo de fuente canónica por campo si entran más proveedores.

**A revisitar:**
- Extender el mismo modelo (freeze + reconciliación) a los otros corporate actions de [[ADR-0014]] (splits, mergers, spinoffs) que también son hechos históricos.

## Action Items

### Fase 1 — Inmutabilidad y lifecycle
1. [ ] Derivar `status` por fechas en lectura; marcar `COMPLETED` como congelado.
2. [ ] Guard de inmutabilidad: prohibir UPDATE/DELETE de filas `COMPLETED` (servicio + trigger/constraint Postgres) + auditoría.
3. [ ] Permitir actualización de filas provisionales (hoy `DividendSyncService` las salta si ya existen) hasta que finalicen.

### Fase 2 — Reconciliación, validación y clasificación
4. [ ] Provenance por campo + fuente canónica configurable; registrar divergencias.
5. [ ] Reglas de validación en ingesta (sanidad de monto, fechas nulas, `distribution_type`).
6. [ ] Clasificador: reparto de acciones/split → `SPLIT`/`SPINOFF` ([[ADR-0014]]), no `DIVIDEND`. Caso de prueba: GOOGL 2014-04-03 ($567.97, fechas nulas) **no** debe entrar como dividendo.
7. [ ] Cola de **cuarentena** para revisión manual de anomalías.

### Fase 3 — Sync eficiente
8. [ ] Separar **backfill profundo** (una vez por símbolo) de **sync incremental** (solo cola provisional + eventos nuevos).
9. [ ] TTL de caché diferenciado: infinito para pagados, corto para provisionales.
10. [ ] Tests con 3 fuentes (Massive/Yahoo/Alpha Vantage) sobre GOOGL: 10 recurrentes idénticos + rechazo del espurio 2014.
