# ADR-0014: Corporate Actions Framework (base `corporate_action` + 7 tipos, identidad FIGI/CIK, dividendos como primer subtipo, Total Return)

**Status:** Proposed
**Date:** 2026-10-18
**Decisiones del owner:** modelar los eventos corporativos con **Class Table Inheritance** — una tabla base `corporate_action` que **relaciona** todos los eventos (NO una Single-Table gigante) + **detalle por tipo** (+ JSONB para la cola larga). Identidad de seguridad por **FIGI/CIK gratuitos** (sin pagar ISIN/CUSIP). **Dividendos** como primer subtipo implementado. El rendimiento del portafolio se calcula como **Total Return** (plusvalía + income de dividendos netos), no como plusvalía plana. No-mutante: los eventos NO alteran la transacción autoritativa.
**Deciders:** Aristeo (owner)
**Módulo:** `marketdata` (catálogo/identidad, ingesta de eventos, detección), `transaction` (entitlement, accrual, Total Return), `portfolio` (performance/charts)
**Relacionado:** [[ADR-0011]] (splits no-mutantes — se vuelve un subtipo), [[ADR-0013]] (fecha de operación wall-time+zona — base del entitlement), [[ADR-0008]] (catálogo single source of truth — se extiende a security master), [[ADR-0009]] (valuación por fecha), [[ADR-0006]] (FX/moneda de presentación), [[ADR-0007]] (transacciones importadas read-only)

## Context

El sistema es un **tracker** (registra la realidad, no ejecuta). Hoy maneja dos eventos corporativos de forma aislada: splits ([[ADR-0011]], tabla `stock_split`, ajuste no-mutante de la cantidad) y un dividendo mínimo (`dividend_detail` con solo ex-date/tipo/tax_withheld, alta manual). Faltan como mínimo **7 eventos corporativos/operativos** que un sistema de grado institucional debe absorber, observados en casos reales de la cartera:

1. **Splits** (directos e inversos, p.ej. **WETO**) — ya resuelto por ADR-0011.
2. **De-SPAC** (fusión con SPAC, p.ej. **CEPT → SECZ**).
3. **Reverse merger** (p.ej. **CRCL**).
4. **Quiebra/liquidación** (posición a ~$0).
5. **Fusión híbrida** (cash + stock).
6. **OPA/Tender + Desenlistamiento** (p.ej. **TRAXION**).
7. **Dividendos en efectivo** (p.ej. **AAPL/GOOGL**).

**Problemas actuales:**
1. **Sin modelo común:** cada evento se trataría ad-hoc → código ramificado por escenario, imposible de escalar a 7+ tipos.
2. **Identidad por ticker (frágil):** el ticker no es identidad estable (misma emisora en NASDAQ y SIC/BMV; renombres FB→META; clases GOOG/GOOGL). Un dividendo o split no matchea de forma fiable las tenencias de uno o varios usuarios si se llavea por el string del ticker.
3. **Rendimiento = plusvalía plana:** como Google Finance/Finviz, el rendimiento ignora los dividendos cobrados. Ej.: GOOGL mostraba `+$206.59` (plusvalía) omitiendo `~$2.10` de dividendos brutos cobrados desde la iniciación de abril-2024.
4. **Detección de iniciaciones:** una emisora que NO pagaba (AMZN hoy) y que inicia dividendos en el futuro requiere un mecanismo que mire hacia adelante, no un backfill.

## Decision

### 1. Identidad: security master por FIGI/CIK (gratis), extendiendo el catálogo
Se **extiende `asset_catalog`** ([[ADR-0008]]) como security master, con identificadores universales **gratuitos**: `composite_figi` (seguridad, composite por país) como **llave canónica**, `share_class_figi` (share-class global, para cross-border US↔SIC), `cik` (emisor SEC, link a filings), `primary_exchange`/MIC, y `isin` **opcional** (nullable, solo si un proveedor lo da gratis — útil para foco europeo). **CUSIP se omite** (licenciado/caro; FIGI lo sustituye). Un servicio de resolución mapea `ticker+exchange → composite_figi` vía **OpenFIGI** (gratis) en la ingesta, cacheado en Redis. Todo (transacciones, holdings, eventos) FK al `security_id` canónico — nunca al ticker.

### 2. Modelo: Class Table Inheritance — base relatora + detalle por tipo
Una tabla **`corporate_action`** con la espina común de **todo** evento (security_id, action_type, status, fechas pivote, proveedor, `provider_event_id` para dedup, `raw_payload` JSONB). Las partes específicas por tipo viven en **tablas de detalle 1:1** (`dividend_detail`, `split_detail`, `merger_detail`, `tender_offer_detail`, `bankruptcy_detail`), y la **cola larga** (spin-offs, rights issues raros) en el `payload` JSONB de la base. Se rechaza la Single-Table gigante (nulls dispersos, sin integridad por tipo) y las tablas-sin-base (pierden timeline/entitlement unificados). `stock_split` (ADR-0011) se reconduce a `split_detail`; `dividend_detail` se enlaza a la base.

### 3. Los 4 motores unificados (una sola implementación para los 7 tipos)
- **Timeline / "Momentos clave":** `corporate_action` por `security_id` ordenado por fecha pivote → marcadores en los charts, para cualquier tipo, una query. Cacheado **global por security** en Redis.
- **Ingesta única:** adapters por proveedor (Massive US primario, **seed por migración para MX/BMV** — mismo patrón que festivos en ADR-0012, porque no hay proveedor MX gratis fiable) con `@Order` fallback, dedup por `provider_event_id`, `raw_payload` para auditoría.
- **Entitlement único:** "¿quién tenía la seguridad antes de la fecha pivote?" con la **fecha de operación** ([[ADR-0013]]) y `<` estricto — idéntico para dividendo (ex-date), split (execution) y fusión (effective). Reusa la regla de ADR-0011.
- **Effects engine (Strategy + eventos Spring Modulith):** `CorporateActionEffective(id)` despacha a un handler por `action_type`: SPLIT→ajusta cantidad (ADR-0011), DIVIDEND→income, MERGER→swap de security_id/acciones, BANKRUPTCY→posición a $0 (realized loss), TENDER→venta forzada al offer price. Agregar un tipo = un handler nuevo, cero cambios en la base.

### 4. Detección de iniciaciones/recortes (los 4 escenarios de dividendos)
No se guarda "es pagador = true/false"; se **proyecta** el estado del stream de eventos. `DividendSyncService` poleé **solo las `security_id` con posición > 0** de cualquier usuario: **backfill** histórico (cubre pagadores existentes) + **poll forward del calendario** (cubre iniciaciones futuras de no-pagadores como AMZN). `DividendStatusProjector` detecta transiciones y publica eventos de dominio (`DividendInitiated`, `DividendRaised`, `DividendCut`) → fan-out a **todos** los usuarios que tengan esa `security_id`. Los 4 escenarios (Regular, Growth, Iniciación, Recorte) **emergen** como patrones del mismo stream, sin código por escenario.

### 5. Dividendos como primer subtipo (con cadena fiscal real)
`dividend_detail` modela el régimen mexicano real (GBM): **retenciones en cadena** — 1ª extranjera (US 30% → **10% con W-8BEN**), 2ª México 10% sobre el neto — vía tabla hija `dividend_withholding` (layer, jurisdiction, rate, base, amount, creditable). Campos: `source_type` (MEXICAN_ISSUER|FOREIGN_SIC|FOREIGN_USA), `gross_amount`, `net_amount`, `dividend_per_share`, `split_adjusted_dps`, `distribution_type` (recurring|special), `frequency`, `price_adjustment_factor`. El **bruto teórico** lo detecta el proveedor (Massive `cash_amount`); el **neto real** lo reconcilia el CFDI (SIC) / 1042-S (Trading USA). DRIP → genera un `BUY` al precio de payment-date.

### 6. Rendimiento = Total Return (no plusvalía plana)
El performance se **descompone**: `Total Return = Unrealized P&L (MV − costBasis) + Realized P&L (ventas) + Income (Σ dividendos netos)`. La UI expone **Price Return / Income Return / Total Return** por separado — el diferenciador frente a Google/Finviz. El income se mantiene por evento (listener suma `dividend_income_net` al holding), no por SUM en cada request. **Dividend growth**: `dividend_annual_summary` (Σ `split_adjusted_dps`, solo recurring, años completos) → CAGR 1/3/5/10a, cacheado **global por security**.

### 7. No-mutante y precisión
Consistente con [[ADR-0011]]/[[ADR-0007]]: los eventos corporativos **no mutan** la transacción autoritativa; sus efectos se **proyectan** (cantidad ajustada, income agregado, swaps como nuevas transacciones). Todo monto en `numeric(18,8)` (reconcilia al centavo con el clearing). Fechas de evento = `LocalDate` (calendario, sin zona — distinto a la EJECUCIÓN de ADR-0013 que sí lleva wall-time+zona).

## Consequences

**Positivas**
- Un solo framework absorbe los 7 tipos (y los que vengan) sin ramificar por escenario.
- Identidad estable (FIGI/CIK) → los eventos matchean correctamente a 1 o N usuarios, cross-plaza, sin pagar ISIN/CUSIP.
- Total Return refleja la realidad económica (dividendos dejan de "desaparecer").
- Timeline/ingesta/entitlement/effects unificados; Redis por velocidad de cambio escala a muchos usuarios/símbolos.
- Reusa lo construido: ADR-0011 (splits), ADR-0013 (fecha operación), ADR-0008 (catálogo), ADR-0006 (FX).

**Negativas / costos**
- Cambio estructural grande (security master + base + detalles + motores) → se hace **por fases**.
- Migrar `stock_split`/`dividend_detail` bajo la base sin romper ADR-0011.
- Resolución FIGI depende de OpenFIGI/proveedor (cacheable, pero es una dependencia nueva).
- MX/BMV sin proveedor de eventos → seed manual (aceptado, como festivos).
- Reconciliación fiscal real depende del CFDI/1042-S (import), no solo del proveedor.

## Plan de implementación (por fases)

- **F1 — Identidad (security master):** columnas `composite_figi`/`share_class_figi`/`cik`/`primary_exchange`/`isin` en `asset_catalog` + servicio de resolución `ticker→figi` (OpenFIGI) + caché Redis. FK `security_id` desde transacción/holding.
- **F2 — Base `corporate_action` + reconducir splits:** tabla base + `split_detail`; migrar `stock_split` (ADR-0011) bajo la base sin cambiar su comportamiento; effects handler SPLIT = el ajuste no-mutante existente.
- **F3 — Dividendos (primer subtipo completo):** `dividend_event` ingesta (MassiveDividendAdapter + seed MX), `dividend_detail` + `dividend_withholding` (cadena fiscal), entitlement/accrual por fecha de operación, detección dual (backfill + forward) con `DividendInitiated/Cut` (Modulith).
- **F4 — Total Return:** componente Income Return + proyección de performance por holding + UI Price/Income/Total + caché Redis `perf:slow` por usuario.
- **F5 — Dividend growth:** `dividend_annual_summary` + CAGR (split-adjusted, recurring, años completos), caché global por security; streak/forward income/YoC.
- **F6 — Otros tipos (bajo demanda):** `merger_detail` (De-SPAC/reverse/hybrid), `tender_offer_detail`, `bankruptcy_detail` + sus effects handlers, conforme aparezcan en cartera (CRCL, CEPT→SECZ, TRAXION…).

## Appendix — Esquema objetivo (resumen)

```sql
-- F1: security master (extiende asset_catalog)
ALTER TABLE asset_catalog
  ADD COLUMN composite_figi   VARCHAR(12),
  ADD COLUMN share_class_figi VARCHAR(12),
  ADD COLUMN cik              VARCHAR(10),
  ADD COLUMN primary_exchange VARCHAR(12),
  ADD COLUMN isin             VARCHAR(12);   -- nullable, free-only

-- F2: base relatora (Class Table Inheritance)
CREATE TABLE corporate_action (
  id                UUID PRIMARY KEY,
  security_id       UUID NOT NULL REFERENCES asset_catalog(security_id),
  action_type       VARCHAR(20) NOT NULL,  -- SPLIT|DIVIDEND|MERGER|TENDER_OFFER|DELISTING|BANKRUPTCY|SPINOFF
  status            VARCHAR(15) NOT NULL,  -- ANNOUNCED|EFFECTIVE|COMPLETED|CANCELLED
  pivotal_date      DATE NOT NULL,         -- ex-date|execution|effective (semántica por tipo)
  declaration_date  DATE, record_date DATE, payment_date DATE,
  source_provider   VARCHAR(20) NOT NULL,
  provider_event_id VARCHAR(80) UNIQUE,
  raw_payload       JSONB,
  synced_at         TIMESTAMPTZ NOT NULL
);

-- detalles 1:1 (ejemplos)
-- split_detail(corporate_action_id, split_from, split_to, share_multiplier)
-- dividend_detail(corporate_action_id, gross_amount, net_amount, dps, split_adjusted_dps,
--                 distribution_type, frequency, source_type, price_adjustment_factor)
-- dividend_withholding(dividend_detail_id, layer_order, jurisdiction, rate, base_amount, amount, creditable)
-- merger_detail(corporate_action_id, target_security_id, exchange_ratio, cash_per_share, stock_ratio)
-- tender_offer_detail(corporate_action_id, offer_price, delisting_flag)
-- bankruptcy_detail(corporate_action_id, final_value)
```

Mapeo de los 7 eventos:

| Evento (caso real) | `action_type` | detalle | efecto (handler) |
|---|---|---|---|
| Split inverso (WETO) | SPLIT | split_detail | ajusta cantidad (ADR-0011) |
| De-SPAC (CEPT→SECZ) | MERGER (DE_SPAC) | merger_detail | swap security_id/acciones |
| Reverse merger (CRCL) | MERGER (REVERSE) | merger_detail | swap |
| Quiebra/liquidación | BANKRUPTCY | bankruptcy_detail | posición → $0, realized loss |
| Fusión híbrida | MERGER (HYBRID) | merger_detail | cash (realized) + stock (nueva posición) |
| OPA/delisting (TRAXION) | TENDER_OFFER / DELISTING | tender_offer_detail | venta forzada al offer price |
| Dividendo (AAPL/GOOGL) | DIVIDEND | dividend_detail + withholding | income (Total Return) |

Fórmulas clave:
```
Total Return      = (MarketValue − CostBasis) + RealizedPnL + Σ DividendNeto
Yield on Cost     = DPS_anual / cost_basis_por_acción
Dividend CAGR(n)  = (DPS_t / DPS_{t-n})^(1/n) − 1        (DPS split-adjusted, solo recurring)
Entitlement       = Σ shares con fecha_de_operación < pivotal_date   > 0
```
