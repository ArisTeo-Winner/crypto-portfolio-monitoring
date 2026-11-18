# ADR-0030: Módulo de Efectivo / Buying Power (saldo de caja multi-moneda)

**Status:** Proposed
**Date:** 2026-11-07
**Deciders:** Aristeo (owner)
**Módulo:** nuevo `cash` (ledger + saldo), con entradas desde `transaction`/`marketdata` vía eventos
**Relacionado:** [[ADR-0029]] (ingresos por dividendo del usuario), [[ADR-0024]]/[[ADR-0027]] (accrual de dividendos), [[ADR-0014]] (corporate actions), [[ADR-0017]] (módulo tax / ISR anual), [[ADR-0010]] (no sumar entre monedas), [[ADR-0028]] (ledger tipo exchange)

## Context

Cuando una emisora paga un **dividendo en efectivo regular**, el bróker deposita el **neto** (tras la retención **en origen**) como **saldo de efectivo / buying power** en la cuenta del usuario, en la **moneda del activo**. Ese efectivo es de uso libre: reinvertir (comprar más), retirar al banco, o mantener en caja.

- GOOGL (US, DriveWealth): dividendo $0.22/acción → retención en origen 10% (W-8BEN) → **neto $0.198 USD** depositado como efectivo.
- BIMBOA (MX, Indeval): dividendo $1.075 MXN/acción → retención en origen 10% ISR → **neto $0.9675 MXN** depositado como efectivo.

**Hoy el sistema no modela efectivo.** El accrual (ADR-0024/0027) registra el dividendo como una transacción `DIVIDEND` con su neto, pero ese neto **no incrementa ningún saldo** — no hay buying power, ni "efectivo sin invertir", ni base para reinvertir/retirar. Es el ítem **"Cash"** del Portfolio de Snowball que no existe en nuestra app.

Fuerzas en juego:
- **Multi-moneda, sin mezclar** (ADR-0010): el efectivo USD (cuenta internacional) y el MXN (cuenta nacional) son **saldos separados**; nunca se suman ni se convierten implícitamente. Buying power se evalúa por moneda.
- **Auditabilidad / ledger** (ADR-0028): el efectivo es dinero; cada movimiento debe ser rastreable y el saldo reconstruible, sin updates perdidos bajo concurrencia.
- **No romper el flujo actual:** hoy registrar una COMPRA no exige fondos. Introducir efectivo **no debe** convertir las compras en operaciones que fallen por saldo insuficiente (eso sería un cambio de comportamiento grande); buying power arranca **informativo**, no como validación bloqueante.
- **DRIP** (reinversión automática): existe el flag `autoReinvestment` en la transacción, hoy inerte; es una fase futura.
- **Dependencia fiscal:** el monto que entra como efectivo = bruto − retención **en origen** (no la cadena US→MX completa). Alinear eso es trabajo del lado fiscal (ver [[ADR-0017]] y el follow-up de corrección de la cadena); este ADR consume el "neto de origen" como entrada, no lo redefine.

## Decision

**Crear un módulo `cash` con un ledger de movimientos de efectivo append-only como fuente de verdad, y un saldo derivado por `(usuario, moneda)`.** El efectivo se acredita por eventos de dominio (dividendo neto, venta) y por operaciones explícitas del usuario (depósito/retiro); el saldo se calcula a partir del ledger. En v1 el buying power es **informativo** (no valida compras). La reinversión automática (DRIP) y la validación de fondos quedan como fases posteriores.

### Modelo

- **`cash_movement`** (append-only, fuente de verdad): `{ id, user_id, currency(3), movement_type, amount(signed, 18,8), occurred_at, source_ref, idempotency_key }`. Nunca se actualiza ni borra; una corrección es un movimiento compensatorio.
  - `movement_type`: `DIVIDEND_CREDIT`, `SELL_CREDIT`, `BUY_DEBIT`, `DEPOSIT`, `WITHDRAWAL`, `FEE_DEBIT`, `INTEREST_CREDIT` (futuro). El signo va en `amount` (créditos +, débitos −) para que el saldo sea una suma pura.
  - `idempotency_key` **único**: p. ej. `dividend:{userId}:{symbol}:{exDate}` — reusa el patrón del accrual; reprocesar no duplica (unique + captura de `DataIntegrityViolation`).
- **Saldo** = `SUM(amount)` por `(user_id, currency)` sobre `cash_movement`. **Derivado** en v1 (lectura), con opción de **materializar** un snapshot `cash_balance(user_id, currency, balance, updated_at)` cuando el volumen lo pida (O(1) para buying power a escala). Append-only + saldo derivado evita el dual-write y los updates perdidos.
- **Aislamiento por moneda** (ADR-0010): nunca un saldo "total"; siempre una lista `[{currency, balance}]`.

### Entradas al ledger (acíclico)

- **Dividendo:** al acreditar el dividendo (transaction), se publica/consume un evento → `cash` inserta `DIVIDEND_CREDIT` con el **neto de origen** en la moneda del activo. El evento viaja por `shared` o por el port-out del publicador (patrón ADR-0024), para no crear ciclo `transaction↔cash`.
- **Depósito/Retiro manual:** endpoints `/me/cash` (POST) → movimiento `DEPOSIT`/`WITHDRAWAL`.
- **Compra/Venta (fase posterior):** `BUY_DEBIT`/`SELL_CREDIT` para que el buying power refleje operaciones; en v1 **no** se cablea (no romper el flujo de alta de transacciones).

### API (user-scoped, ROLE_USER)

```
GET  /api/v1/me/cash            → [{ currency, balance }]            (saldos por moneda)
GET  /api/v1/me/cash/movements  → [{ id, currency, movementType, amount, occurredAt, sourceRef }]
POST /api/v1/me/cash/deposit    → { currency, amount }   (DEPOSIT)
POST /api/v1/me/cash/withdraw   → { currency, amount }   (WITHDRAWAL; valida saldo suficiente)
```

## Options Considered

### Option A: Saldo derivado como proyección sobre el ledger de transacciones (sin módulo nuevo)
Cash = Σ firmada de las transacciones del usuario por moneda (DIVIDEND +neto, SELL +proceeds, BUY −costo) + nuevos tipos `DEPOSIT`/`WITHDRAWAL`.
| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Escalabilidad | Media (recomputa; cacheable) |
| Acoplamiento | Reusa el ledger existente; cero módulo nuevo |

**Pros:** sin storage nuevo, sin dual-write, siempre consistente con el ledger. **Cons:** acopla la semántica de efectivo a los tipos de transacción; obliga a modelar depósitos/retiros y hasta intereses como "transacciones" (que no son operaciones bursátiles); mezcla conceptos (una transacción es por-activo, el efectivo es por-moneda). No modela naturalmente movimientos que no son transacciones de activo.

### Option B: Módulo `cash` dedicado, ledger append-only como fuente de verdad + saldo derivado (materializable) — ELEGIDA
| Dimensión | Evaluación |
|---|---|
| Complejidad | Media |
| Escalabilidad | Alta (append-only particionable; snapshot O(1) cuando duela) |
| Acoplamiento | Acíclico vía eventos (cash no es importado por nadie) |

**Pros:** separa efectivo (por-moneda) de transacciones (por-activo); modela naturalmente dividendo/venta/depósito/retiro/interés; ledger = auditoría; append-only evita updates perdidos; idempotente; materializar el saldo es una optimización posterior, no una decisión de hoy. Coincide con el "Cash" de Snowball como concepto propio. **Cons:** un módulo más; plumbing de un evento de crédito; si luego se materializa el saldo, aparece reconciliación snapshot↔ledger.

### Option C: Ledger + saldo materializado desde el día 1 (dual-write)
Como B pero manteniendo `cash_balance` actualizado en cada movimiento.
| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta |
| Escalabilidad | Alta (O(1) saldo) |
| Riesgo | Dual-write → divergencia ledger/saldo |

**Pros:** buying power O(1) inmediato. **Cons:** introduce el dual-write y su reconciliación **antes** de que la escala lo justifique; el proyecto ya mostró que derivar-primero y materializar-después (ADR-0028) es el orden correcto. Descartada como v1; es la evolución de B.

## Trade-off Analysis

El eje es "¿reusar el ledger de transacciones o un ledger de efectivo propio, y derivar o materializar el saldo?". El efectivo es **por-moneda** y abarca eventos que **no** son transacciones de activo (depósitos, retiros, intereses), así que forzarlo dentro del ledger de transacciones (A) ensucia ambos conceptos. Un ledger de efectivo propio (B) los separa limpio y se mantiene acíclico vía eventos, como ya se hizo con dividendos (ADR-0024). Materializar el saldo desde el día 1 (C) mete dual-write y reconciliación antes de que la escala lo pida; el patrón validado del repo es **derivar primero, materializar cuando duela** (igual que ADR-0028 F2/F3). Se elige **B**: módulo `cash`, ledger append-only como verdad, saldo derivado, snapshot como fase posterior.

## Consequences

- **Más fácil:** el neto del dividendo deja de evaporarse — entra como efectivo real y habilita "efectivo sin invertir", base para reinversión manual y retiro; el valor del portafolio puede incluir caja (holdings + cash) por moneda; auditoría completa de movimientos.
- **Más difícil / a vigilar:** nuevo módulo y un evento de crédito; el **neto correcto** depende de alinear la cadena fiscal (hoy retiene US+MX ≈19%; el efectivo debe recibir solo el neto **de origen** — es un prerequisito/cross-ref, no parte de este ADR); si se materializa el saldo (fase posterior) habrá que reconciliar.
- **A revisar:** cuándo materializar el saldo (snapshot por `(user,currency)`); si/ cuándo las COMPRAS pasan a **debitar** efectivo y el buying power se vuelve **validación** (hoy informativo); DRIP (autoReinvestment → `BUY` automático desde el `DIVIDEND_CREDIT`); intereses tipo Smart Cash.

## Action Items

### v1 (núcleo, no-bloqueante)
1. [ ] Módulo `cash` (hexagonal): entidad `CashMovement` (append-only) + migración `cash_movement` (índice por `user_id,currency`; único por `idempotency_key`).
2. [ ] `CashLedgerService`: `credit/debit` idempotente (unique + `DataIntegrityViolation`); `balancesByCurrency(userId)` = Σ firmada por moneda; `movements(userId)`.
3. [ ] Entrada de dividendo: evento de crédito (en `shared` o port-out del publicador para evitar ciclo) consumido por `cash` → `DIVIDEND_CREDIT` con el neto de origen, en la moneda del activo, idempotente por `(userId,symbol,exDate)`.
4. [ ] REST user-scoped: `GET /me/cash`, `GET /me/cash/movements`, `POST /me/cash/deposit`, `POST /me/cash/withdraw` (retiro valida saldo ≥ monto). Matcher `"/api/v1/me/cash/**".hasRole("USER")`.
5. [ ] Tests: unit del ledger (saldo por moneda, idempotencia, retiro sin fondos rechazado) + IT del crédito por dividendo + Modulith/ArchUnit (nuevo módulo acíclico).

### Follow-up (fases posteriores)
6. [ ] Prerequisito de correctitud: alinear la cadena fiscal para que el neto acreditado = bruto − retención **de origen** (ruteo US vs MX por domicilio), con el ISR MX como obligación **anual** (tax, ADR-0017). Sin esto, el efectivo acreditado no cuadra con el bróker.
7. [ ] Materializar saldo (`cash_balance` snapshot por `(user,currency)`) cuando la escala de lectura lo pida; reconciliación contra el ledger.
8. [ ] Compras/ventas mueven efectivo (`BUY_DEBIT`/`SELL_CREDIT`) y buying power pasa de informativo a **validación** de fondos (decisión de producto).
9. [ ] DRIP: `autoReinvestment` activo → el `DIVIDEND_CREDIT` dispara una COMPRA automática de la emisora (fracciones) y deja saldo neto 0 de ese dividendo.
10. [ ] Valor del portafolio incluye caja por moneda (holdings + cash) en el summary.
