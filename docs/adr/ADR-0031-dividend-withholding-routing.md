# ADR-0031: Ruteo de la retención fiscal del dividendo por (fuente × cuenta)

**Status:** Proposed
**Date:** 2026-11-08
**Deciders:** Aristeo (owner)
**Módulo:** `transaction` (accrual / cálculo del neto), con dato de domicilio desde `asset`/`marketdata`
**Relacionado:** [[ADR-0029]] (neto en /me/dividends), [[ADR-0030]] (efectivo: el neto es el crédito), [[ADR-0017]] (tax: ISR anual), [[ADR-0014]] (accrual F3c), [[ADR-0010]] (monedas separadas)

## Context

El accrual calcula el **neto** de cada dividendo con `DividendTaxCalculator.foreignCashDividend(...)`, que aplica **siempre** la cadena **US 10% → MX 10% sobre el neto** (≈19% efectivo), sin importar el activo ni la cuenta. Eso alimenta el `netAmount` de `/me/dividends` (ADR-0029) y será el crédito de efectivo (ADR-0030). Pero la retención real **depende del origen del dividendo y del producto/custodia**, confirmado por la FAQ oficial de GBM ("¿Cómo funcionan los impuestos sobre los dividendos en Trading MX?") y la guía fiscal 2026:

- **Emisora mexicana (BMV)** — p. ej. BIMBOA: **10% único, definitivo** (utilidades 2014+). **Sin** pata extranjera.
- **Emisora extranjera vía SIC** (comprada en pesos, custodia MX) — p. ej. GOOGL en SIC: **US 10%** (W-8BEN; 30% sin él) **+ MX 10% sobre el monto neto** (después de la retención extranjera). GBM retiene ambas en el depósito y **emite CFDI**. ← es exactamente la cadena que hoy aplicamos a TODO.
- **Emisora extranjera vía bróker US** (Trading USA / DriveWealth, cuenta USD) — p. ej. GOOGL en USD: **solo la retención de origen (US 10%)** en el depósito; el **ISR MX es anual** (no se retiene en el evento), **sin CFDI**.

Resultado: nuestra cadena única es correcta **solo** para el caso SIC. Para los dos holdings reales del usuario está **mal**:
- **GOOGL (Trading USA, USD):** hoy sobre-retenemos (19% en vez de 10%).
- **BIMBOA (BMV, MXN):** hoy le agregamos una retención US inexistente (debería ser 10% MX único).

El determinante NO es solo el domicilio del activo, sino **(domicilio de la fuente del dividendo × cuenta/custodia)**.

## Decision

**Rutear el cálculo de retención del dividendo por un perfil fiscal derivado de (fuente × cuenta)**, en vez de aplicar la cadena US→MX a todo. El neto-al-depósito refleja lo que el bróker retiene realmente; el ISR MX que es **obligación anual** (caso bróker US) NO se retiene en el evento del dividendo — se difiere al módulo tax (ADR-0017). Las tasas siguen externalizadas (`DividendTaxRates`, `dividend.tax.*`).

### Perfiles fiscales (matriz)

| Perfil | Cuándo | Retención en el depósito | Neto al depósito | Anual (tax) |
|---|---|---|---|---|
| `MX_DOMESTIC` | emisora mexicana (BMV) | **10% MX** (definitiva) | `gross × 0.90` | acreditable 30% emisora |
| `FOREIGN_VIA_LOCAL_CUSTODY` (SIC) | emisora extranjera, custodia MX/pesos | **US 10% + MX 10% sobre neto** | `gross × 0.9 × 0.9` | acredita retención extranjera; CFDI |
| `FOREIGN_VIA_FOREIGN_BROKER` | emisora extranjera, bróker US/USD (Trading USA) | **solo origen (US 10%)** | `gross × 0.90` | ISR MX anual pendiente |

`FOREIGN_VIA_LOCAL_CUSTODY` = la cadena actual (se conserva tal cual). Los otros dos son nuevos.

### Entrada del ruteo

El perfil se resuelve de:
1. **Domicilio de la fuente del dividendo** (MX vs extranjero): del catálogo de activos (*security identity*, ADR-0019) o, como heurística de respaldo, `currency`/`exchange`.
2. **Cuenta/custodia** (MX-pesos vs bróker-US): de la transacción (`broker`, `currency`, `exchange`). Ej.: foránea en USD → bróker US; foránea en MXN → SIC; nacional MXN → BMV.
3. **Default seguro** cuando falte dato: perfil que no sobre-retenga (p. ej. solo origen), registrando el símbolo para revisión — nunca inventar una retención de más.

## Options Considered

### Option A: Ruteo por perfil (fuente × cuenta) — ELEGIDA
| Dimensión | Evaluación |
|---|---|
| Complejidad | Media |
| Correctitud | Alta (cubre los 3 casos reales) |
| Datos | Requiere domicilio (catálogo) + señal de cuenta (transacción) |

**Pros:** neto correcto por holding; separa lo retenido-en-depósito de lo anual (tax); conserva la cadena SIC existente como un perfil. **Cons:** necesita el domicilio del activo y una señal fiable de cuenta/producto; casos ambiguos (foránea en MXN) exigen el domicilio del catálogo.

### Option B: Mantener la cadena única US→MX (status quo)
**Pros:** cero cambios. **Cons:** incorrecta para 2 de 3 casos, incluidos **ambos** holdings del usuario; el efectivo (ADR-0030) nacería con un neto equivocado. Descartada.

### Option C: Tasa plana configurable por activo (sin perfiles)
Un `withholdingRate` por símbolo en config/catálogo.
**Pros:** simple. **Cons:** no modela la **cadena** (origen + local sobre neto) del caso SIC ni la distinción depósito-vs-anual; pierde la semántica fiscal. Descartada; los perfiles son más expresivos y auditables.

## Trade-off Analysis

El eje es "¿una regla única o ruteo por caso, y qué tan fina es la clasificación?". La FAQ de GBM deja claro que hay **tres** tratamientos distintos y que el nuestro solo cubre uno; mantenerlo (B) deja mal el neto de ambos holdings del usuario y contaminaría el efectivo. Una tasa plana (C) pierde la cadena y la distinción depósito/anual que el propio fisco hace. El ruteo por perfil (A) es el que refleja la realidad con el costo de necesitar domicilio + señal de cuenta; el repo ya tiene *security identity* en el catálogo y `broker/currency/exchange` en la transacción, así que el dato es alcanzable. Se elige **A**, con default conservador cuando el dato falte.

## Consequences

- **Más fácil:** `/me/dividends.netAmount` correcto por holding; el crédito de efectivo (ADR-0030) nace con el neto real; la base para el ISR anual (tax, ADR-0017) queda separada y explícita.
- **Más difícil / a vigilar:** hay que resolver el domicilio del activo y la cuenta de forma fiable; el caso "foránea en pesos (SIC)" vs "nacional en pesos (BMV)" depende del domicilio del catálogo; definir el default cuando no se conoce (sin sobre-retener).
- **A revisar:** re-cálculo de los dividendos ya acreditados con la cadena vieja (backfill del neto) — decisión aparte; CFDI solo existe en SIC/BMV, no en Trading USA (dato para el reporte fiscal); cuando el módulo tax tome el ISR anual, no doble-contar lo ya retenido.

## Action Items

1. [ ] Enum `DividendWithholdingProfile { MX_DOMESTIC, FOREIGN_VIA_LOCAL_CUSTODY, FOREIGN_VIA_FOREIGN_BROKER }` + método en `DividendTaxCalculator` por perfil (el actual `foreignCashDividend` pasa a ser `FOREIGN_VIA_LOCAL_CUSTODY`; nuevos: `domesticCashDividend` = 10% único, `foreignViaForeignBroker` = solo origen).
2. [ ] Resolver el perfil en el accrual: domicilio del activo (catálogo / *security identity*, con heurística `currency`/`exchange` de respaldo) × señal de cuenta (`broker`/`currency`/`exchange` de la transacción). Default conservador + log si falta dato.
3. [ ] Propagar el perfil elegido al `DividendDetail` (opcional, para auditoría/CFDI) o dejarlo implícito en los montos.
4. [ ] Tests: unit del cálculo por perfil (BIMBOA 10% único; GOOGL Trading USA 10% US; GOOGL SIC cadena) + del ruteo (clasificación por los datos del holding) + IT del accrual con cada perfil.
5. [ ] (follow-up) Backfill/recálculo del neto de dividendos ya acreditados con la cadena vieja; coordinación con tax (ADR-0017) para el ISR anual sin doble conteo; prerequisito cumplido para el crédito de efectivo (ADR-0030).
