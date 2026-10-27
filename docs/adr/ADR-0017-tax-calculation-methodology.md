# ADR-0017: Metodología de cálculo fiscal (ISR) por régimen — calculadora anual sobre estados GBM/DriveWealth con costeo por régimen y FX fiscal Banxico

**Status:** Accepted
**Date:** 2026-10-24
**Deciders:** Aristeo (owner)
**Módulo:** nuevo **`tax`** (orquesta); consume `portfolio` (engines FIFO/ACB), `transaction` (dividendos), `marketdata` (FX Banxico), `statementimport` (ingesta)
**Relacionado:** [[ADR-0002]] (CredentialsIT), [[ADR-0003]] (fricción GBM comisión/IVA/fees), [[ADR-0008]] (catálogo), [[ADR-0009]] (trade-date FX de valuación), [[ADR-0011]] (splits), [[ADR-0014]] (corporate actions), y el diseño async de [[project_statementimport_async_design]]. Relacionado con [[project_mxn_costbasis_fx_distortion]] (el costo fiscal se lleva en MXN, no se re-expresa).

> **Disclaimer:** esta es una herramienta de **estimación/verificación**, NO asesoría fiscal. Las tasas, artículos y convenciones aquí son base metodológica a **validar con un contador/fiscalista**; el régimen fiscal MX cambia y el broker (GBM) ya retiene y reporta parte de esto.

## Context

El usuario quiere una **calculadora de impuestos**: leer estados de GBM, extraer compras/ventas/dividendos, cargar tipos de cambio de Banxico y calcular con FIFO. Al mapear el repo, **~70% del pipeline ya existe**:

- **Ingesta**: módulo `statementimport` ya parsea GBM (`GbmStatementParser`, `GbmEquityConfirmationParser`) y DriveWealth, async con reintentos.
- **Extracción**: módulo `transaction` modela BUY/SELL/DIVIDEND con comisión/IVA/fees ([[ADR-0003]]) y `dividend_detail`.
- **FX Banxico**: `fx_rate_daily` + `usdMxnRateOn()` ([[ADR-0009]]), `BanxicoRateAdapter` ([[ADR-0008]]).
- **Costeo**: **ambos engines ya existen** — `FifoEngine` y `AcbEngine` (costo promedio) en `portfolio.domain.engine`, vía `RealizedPnLService.calculateFifo()/calculateAcb()`.
- **Dividendos**: feed **ya cableado de punta a punta** — `MassiveDividendAdapter` (consume `GET /stocks/v1/dividends?ticker=…`) → `DividendSyncService` persiste `corporate_action(DIVIDEND)` + `dividend_action_detail` (mapea `cash_amount`, `currency`, `frequency`, `distribution_type`, `split_adjusted_cash_amount`, `historical_adjustment_factor`, record/pay/declaration/ex dates), idempotente por `provider_event_id` → `DividendDetectedEvent` → accrual por usuario → `DividendTaxCalculator`/`DividendTaxRates` (W-8BEN 10% US + 10% MX).

Lo que **falta** es la **capa de agregación fiscal anual (ISR)** por régimen encima de lo anterior. El punto fino: **no es un 10% universal**; cada instrumento tiene régimen distinto, y el método de costeo "correcto" depende del régimen.

## Decision

Crear un módulo **`tax`** que produce un **reporte fiscal anual por régimen**, reutilizando los engines y motores existentes vía puertos. Decisiones de metodología:

### 1. Costeo **por régimen** (decidido)
- **Acciones en bolsa MX (BMV/SIC)** → **costo promedio** (`AcbEngine`), que es lo que prescribe el Art. 129 LISR y lo que calcula la constancia de GBM.
- **Activos US (DriveWealth) y PnL interno** → **FIFO** (`FifoEngine`).
- El ruteo lo determina el **mercado/jurisdicción** del holding (catálogo, [[ADR-0008]]). Ambos engines ya existen: esto es ruteo, no motor nuevo.

### 2. Régimen por tipo de instrumento (no un 10% universal)
| Instrumento | Régimen (base, a validar) | Costeo |
|---|---|---|
| Acciones en bolsa BMV/SIC | ISR cedular **10%** sobre ganancia **anual neta**; pérdidas amortizables hasta **10 años** (Art. 129) | costo promedio |
| Acciones US (no bolsa MX) | ganancia realizada; acreditamiento de impuesto extranjero si aplica | FIFO |
| Dividendos | MX: 10% adicional (Art. 140); extranjeros: **acreditamiento** del retenido afuera (W-8BEN 10%) | motor `DividendTaxCalculator` + agregación anual |
| Intereses / CETES | interés **real** (ajuste por inflación), Art. 133-135 | motor nuevo (F3) |
| Cripto | enajenación de bienes / "demás ingresos" (NO el 10% de bolsa) | FIFO, régimen aparte (F3) |

### 3. FX **fiscal** (distinto del de valuación)
Para ISR se usa el **TC del DOF para solventar obligaciones** (Art. 20 CFF) del día del ingreso = **FIX publicado el día hábil anterior**. Es **distinto** de la tasa de valuación de presentación. Costo y producto se llevan **en MXN** a su fecha respectiva (sin re-expresión posterior, a diferencia de [[project_mxn_costbasis_fx_distortion]]). Confirmar la serie Banxico exacta en el adapter.

### 4. Estado anual persistente
Tabla de **carryforward de pérdidas** por régimen y por año fiscal (hasta 10 años para bolsa), para encadenar declaraciones.

### 5. Reconciliación y alcance
El reporte debe **cotejarse contra la constancia anual de GBM** (que ya retiene/reporta bolsa). Es estimación/verificación, no sustituto de la declaración. Todo output lleva el disclaimer.

## Options Considered

### Costeo (decidido: por régimen)
| Opción | Pro | Con |
|---|---|---|
| **Por régimen** ✅ | fiel al SAT en bolsa (costo promedio) y a tu criterio FIFO en US | ruteo por mercado |
| FIFO para todo | simple | difiere de SAT/constancia GBM en bolsa |
| Costo promedio para todo | alineado a bolsa | menos fiel en US |

### Ubicación del cálculo
- **Opción A — nuevo módulo `tax`** (recomendada): separa la preocupación fiscal; consume `portfolio`/`transaction`/`marketdata` vía puertos (named interfaces, [[reference_modulith_named_interfaces]]). Más limpio, aísla reglas fiscales volátiles.
- **Opción B — dentro de `transaction`**: menos módulos; pero mezcla cálculo fiscal anual con el ciclo de vida de transacciones, y el dividend tax ya vive aquí mientras los engines viven en `portfolio` (quedaría partido igual).

## Trade-off Analysis

El costeo por régimen es el único que cuadra simultáneamente con el SAT (bolsa→costo promedio) y con la expectativa del usuario (FIFO en US), y **no cuesta motor nuevo** porque ambos engines existen. El módulo `tax` separado (Opción A) paga un poco de plumbing de puertos a cambio de aislar reglas fiscales que cambian cada año y evitar inflar `transaction`. El riesgo real no es el código sino la **corrección fiscal** (artículos, FX fiscal, acreditamientos): por eso el disclaimer y la reconciliación contra la constancia son parte del diseño, no un extra.

## Consequences

**Más fácil:**
- Reporte fiscal anual por régimen, reconciliable con GBM, reutilizando ingesta + FIFO/ACB + FX + dividend tax ya existentes.
- Base para futuras automatizaciones (prellenado SAT) sin reescribir motores.

**Más difícil / a vigilar:**
- **Corrección fiscal**: tasas/artículos a validar con fiscalista; el feature debe versionar la metodología por año.
- **FX fiscal** ≠ FX de valuación: dos convenciones conviviendo; documentarlo para no confundirlas.
- Ruteo por mercado depende del catálogo ([[ADR-0008]]) y de los corporate actions ([[ADR-0011]], [[ADR-0014]]) que ajustan costo/tenencia.
- Cross-module: `tax` consume puertos, respeta `ModulithVerificationTest`/ArchUnit ([[reference_modulith_named_interfaces]]).

**A revisitar:**
- Interés real de CETES e impuesto de cripto (F3) tienen reglas propias.
- Integración con el ADR de **moneda funcional** pendiente (costo base real en MXN) — se alinea con el FX fiscal de aquí.

## Action Items

### Fase 1 — Acciones en bolsa (núcleo)
1. [ ] Módulo `tax` con puertos a `portfolio` (lotes/realized FIFO+ACB), `marketdata` (FX fiscal), `statementimport`/`transaction` (operaciones).
2. [ ] Ruteo de costeo por mercado del holding (BMV/SIC→ACB; US→FIFO) usando catálogo [[ADR-0008]].
3. [ ] FX **fiscal** Banxico (TC DOF/FIX día hábil anterior); confirmar serie y fecha exactas; costo/producto en MXN por fecha.
4. [ ] Agregación anual: ganancia neta, 10% cedular, pérdidas amortizables (tabla carryforward por año/régimen).
5. [ ] Reporte fiscal anual (por régimen) descargable + **disclaimer** + bloque de reconciliación vs constancia GBM.
6. [ ] Tests: unit del agregador fiscal (casos con pérdidas, carryforward, split en medio) + IT sobre estados GBM de ejemplo.

### Fase 2 — Dividendos (ingesta/detección/retención YA existen)
7. [ ] **No** hay trabajo de proveedor: la ingesta (`MassiveDividendAdapter`/`DividendSyncService`), el detalle (`dividend_action_detail`) y la retención (`DividendTaxCalculator`) ya corren. Falta solo: **agregación anual** por usuario + **acreditamiento** del impuesto extranjero + **FX fiscal a `pay_date`** (TC DOF). Usar el `cash_amount` **real** (no `split_adjusted_cash_amount`, que es para growth/charting) × tenencia a `record_date`.

### Fase 3 — Intereses/CETES y Cripto
8. [ ] Interés **real** CETES (ajuste inflación, Art. 133-135).
9. [ ] Régimen de **cripto** (enajenación de bienes), separado del 10% de bolsa.
