# ADR-0010: Moneda de presentación (base USD + display currency por preferencia)

**Status:** Proposed
**Date:** 2026-09-26
**Deciders:** Aristeo (owner backend) — coordinación con la sesión de frontend
**Módulo:** `user` (preferencia), `portfolio` (valuación/DTOs), `marketdata` (FX)
**Relacionado:** [[ADR-0006]] (normalización FX v1), [[ADR-0008]] (Banxico primario), [[ADR-0009]] (trade-date FX)

## Context

El usuario quiere que, al elegir **MXN** como "moneda por defecto", todas las tarjetas del dashboard
se muestren en MXN. Estado real verificado en código (backend + frontend):

- **La valuación es base USD unidireccional.** `portfolio_entry` (y holdings-performance e history)
  emiten importes en USD; el costo MXN ya se normaliza a USD ([[ADR-0009]]). No existe ninguna capa
  que convierta USD→otra moneda para mostrar.
- **Hay DOS controles de moneda distintos en el frontend, y ninguno alimenta la presentación:**
  1. **/settings/preferences → "Moneda por defecto"**: `updatePreferences()` solo escribe
     `localStorage("cpm.settings.preferences")`. **No llama a ningún endpoint.** `endpoints.settings.preferences
     = "/api/v1/me/preferences"` es **referencia muerta** (no existe en backend). Es **no-op/cosmético**.
     Los demás campos de esa tarjeta (PnL, prioridad de proveedor, frecuencia de sync, rango de gráfico,
     auto-sync) también son **client-only** en el mismo localStorage.
  2. **/settings → pestaña Cuenta → `preferredCurrency` (USD/EUR/MXN)**: **sí persiste** en el backend.
     Carga con `GET /api/v1/users/me` y guarda con `PUT /api/v1/users/me`
     ([UserController.updateMe](../../src/main/java/com/mx/cryptomonitor/user/infrastructure/inbound/rest/UserController.java),
     `UserMeUpdateRequest.preferredCurrency`, columna `users.preferred_currency VARCHAR(3) DEFAULT 'USD'`).
     Pero **tampoco se consume** para formatear/convertir nada.
- El dashboard formatea con `formatCurrency` (USD); las tablas muestran la moneda **nativa** por fila
  (`transaction.currency`).
- **Restricción:** el selector ofrece **EUR**, pero solo existe el par FX **USD/MXN** (Banxico). No hay
  fuente para USD/EUR.

Conclusión: la preferencia de moneda **no está cableada end-to-end**; falta (a) una única fuente de
verdad, (b) leerla en el path de presentación, y (c) la capa de conversión (que no existe).

## Decision

1. **Fuente de verdad = `users.preferred_currency`** (control de la pestaña **Cuenta**, `GET/PUT
   /api/v1/users/me`, que ya persiste). El selector de **/settings/preferences** se **reconecta** a esa
   misma fuente o se **deprecia** (no puede seguir siendo un localStorage no-op que contradiga a Cuenta).
2. **Separar moneda funcional (base) de moneda de presentación** (IAS 21):
   - **Base = USD**: almacenamiento y **todo** el cálculo de P&L/costo quedan intactos ([[ADR-0006]]/[[ADR-0009]]).
   - **Presentación = `preferred_currency`**: solo formato de salida.
3. **Convertir en el borde de lectura del backend** (no en storage, no en el cliente): un único
   `MoneyPresentationConverter` reutilizado por portfolio/holdings-performance/history. La respuesta se
   vuelve **auto-descriptiva** con un envelope de presentación.
4. **Los porcentajes son invariantes de moneda** → **no se convierten** (un +46.55% es igual en USD y MXN).
   Solo se convierten importes absolutos.
5. **Alcance v1 = USD↔MXN.** EUR queda deshabilitado (o `409`/aviso) hasta tener par USD/EUR.

### Contrato (aditivo)
Los DTO de dinero emiten importes en `displayCurrency` y añaden:
```
presentation { baseCurrency:"USD", displayCurrency, fxRate, rateProvider, rateAsOf }
```
El frontend formatea por `displayCurrency` y **retira** la inferencia de moneda por sufijo `*` y la
doble división (deuda señalada en el reveal de "Balance neto").

### Regla de redondeo
Toda la aritmética en USD; **convertir la cifra final una sola vez** (`MONEY_SCALE=2, HALF_UP`) para no
descuadrar centavos al sumar partes convertidas.

### Tasa
- Valores actuales → spot vigente (`FxRatePort`, FIX Banxico, `market_fx_snapshot`).
- Serie histórica del chart → **v1: toda al spot de hoy** (snapshot coherente); **v2: cada punto al FIX
  de su fecha** (`fx_rate_daily`, [[ADR-0009]]).
- Sin tasa disponible → no convertir, devolver `displayCurrency:"USD"` con aviso (sin inventar).

## Options Considered

### Fuente de verdad de la preferencia

#### Opción A: Unificar en `users.preferred_currency` (elegida)
| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja (ya persiste vía `PUT /users/me`) |
| Consistencia | Alta (una sola fuente) |
| Familiaridad | Alta (patrón existente en el módulo user) |

**Pros:** ya existe columna + endpoint + validación ISO-4217; una sola verdad. **Cons:** hay que
reconectar/deprecar el control de /settings/preferences (hoy localStorage).

#### Opción B: Nuevo store de preferencias (`/api/v1/me/preferences` + tabla)
**Pros:** agrupa PnL/proveedor/sync/moneda en un solo recurso. **Cons:** endpoint hoy inexistente;
duplica la moneda que ya vive en users; más superficie. **Rechazada para moneda** (sí candidata futura
para el resto de preferencias hoy client-only).

### Dónde se convierte USD→display

#### Opción A: Backend en el borde de lectura (elegida)
**Pros:** una sola conversión y redondeo; auditable (estampa la tasa); todos los clientes consistentes.
**Cons:** las respuestas pasan a ser currency-stateful (metadata obligatoria).

#### Opción B: Cliente (multiplica USD×tasa al render)
**Pros:** cero cambio backend. **Cons:** cada cliente re-implementa; drift de redondeo; perpetúa la
heurística frágil del sufijo `*`. **Rechazada.**

## Trade-off Analysis

- **Correctud vs. simplicidad:** mantener base USD y convertir solo en presentación preserva P&L y el
  FX de costo ([[ADR-0009]]) sin tocar storage; el costo es una capa de mapeo + metadata en el contrato.
- **Presentación vs. costo (ADR-0009):** son ortogonales. [[ADR-0009]] convierte *costo MXN→USD* para
  calcular bien el P&L base; este ADR muestra ese base USD en MXN. No encadenar dos veces.
- **EUR:** el selector promete algo sin fuente FX; mejor deshabilitar que mostrar un número inventado.

## Consequences

- **Más fácil:** un punto de conversión → dashboard, history y performance coherentes en la moneda elegida.
- **Más difícil:** el contrato de dinero se vuelve currency-stateful (envelope obligatorio); el frontend
  debe migrar a formatear por `displayCurrency`.
- **Se limpia:** desaparece la inferencia por `*` y la doble división del cliente; se resuelve el control
  duplicado (Cuenta vs preferences).
- **A revisar:** serie histórica al FIX por fecha (v2); soporte EUR; mover el resto de preferencias
  client-only a backend si se decide (Opción B para no-moneda).

## Action Items
1. [ ] Definir enum de monedas soportadas (`USD`,`MXN`) y validarlo en `preferred_currency`.
2. [ ] `MoneyPresentationConverter` + puerto; reutilizado por portfolio/holdings-performance/history.
3. [ ] Envelope `presentation{baseCurrency,displayCurrency,fxRate,rateProvider,rateAsOf}` en los DTOs de
   dinero; **no** convertir porcentajes.
4. [ ] Resolver `displayCurrency` del usuario autenticado (una vez por request).
5. [ ] Frontend: formatear por `displayCurrency`; retirar inferencia por `*` y doble división;
   **reconectar o deprecar** el selector de /settings/preferences a `users.preferred_currency`.
6. [ ] EUR: deshabilitar en el selector hasta tener par USD/EUR.
7. [ ] (v2) serie histórica al FIX por fecha; (opcional) recurso `/me/preferences` para el resto de
   preferencias hoy client-only.
