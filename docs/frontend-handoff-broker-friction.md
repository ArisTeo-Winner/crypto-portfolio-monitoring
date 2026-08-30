# Handoff a Frontend — Fricción de corretaje e importación de comprobantes

**Audiencia:** equipo frontend
**Backend:** crypto-portfolio-monitoring (Spring Boot)
**Estado:** backend completo y probado. Ruta DriveWealth/USD contra datos reales; ruta GBM-MX/MXN
contra ejemplo sintético (pendiente calibrar regex con un PDF real — no afecta el contrato de API).
**Relacionado:** `docs/adr/ADR-0003-broker-friction-net-cost.md`

Este documento describe **solo el contrato de API** que el frontend consume. Toda la lógica (parseo,
cálculo, persistencia) vive en el backend; el frontend únicamente lee JSON y lo pinta.

---

## 1. Cómo obtener el contrato tipado (OpenAPI)

El backend expone OpenAPI 3 vía springdoc. **Recomendado:** generar el cliente TypeScript desde el
spec en vez de copiar shapes a mano.

1. Arranca el backend con springdoc habilitado:
   - En `dev` ya está ON.
   - En otro entorno, exporta `SPRINGDOC_API_DOCS_ENABLED=true` (y opcional `SPRINGDOC_SWAGGER_UI_ENABLED=true`).
2. Spec JSON: `GET http://localhost:8080/v3/api-docs`
3. Swagger UI (exploración): `http://localhost:8080/swagger-ui.html`
4. Genera el cliente con tu herramienta (openapi-typescript, orval, openapi-generator, etc.).

Los tipos de `FrictionBreakdownView`, `TransactionDetailsResponse`, `StatementImportJobResponse` y el
enum `reviewStatus` salen tipados automáticamente.

---

## 2. Autenticación

Todos los endpoints de este documento requieren **JWT Bearer**:

```
Authorization: Bearer <accessToken>
```

El `accessToken` se obtiene de `POST /api/v1/auth/login`. En el OpenAPI están marcados con el esquema
de seguridad `bearerAuth`.

---

## 3. Detalle de transacción — el desglose de fricción

### Endpoint

```
GET /api/v1/me/transactions/details/{transactionId}
```

### Respuesta (`TransactionDetailsResponse`)

Ejemplo real (compra CRCL vía DriveWealth):

```jsonc
{
  "id": "…",
  "assetSymbol": "CRCL",
  "assetType": "STOCK",
  "transactionType": "BUY",          // BUY | SELL | REDEEM | DIVIDEND | TRANSFER
  "transferType": null,
  "transactionDate": "2025-06-11T00:00:00Z",
  "quantity": 0.95368698,
  "pricePerUnit": 108.7988,
  "grossAmount": 103.76,             // cantidad × precio
  "fee": 0.25,                       // fricción total (= totalFrictionCost)
  "feeCurrency": "USD",
  "netAmount": 104.01,               // BUY: gross + fee ; SELL: gross − fee
  "amountLabel": "Total Spent",      // etiqueta lista para UI segun el tipo
  "notes": null,                     // nota libre del usuario (ya no lleva marcadores tecnicos)
  "source": "DRIVEWEALTH",           // MANUAL | DRIVEWEALTH | GBM_STATEMENT | GBM_EQUITY (procedencia)
  "exchange": null,
  "status": "COMPLETED",
  "frictionBreakdown": {             // ← el desglose de auditoria
    "grossAmount": 103.76,
    "brokerCommission": 0.25,
    "brokerIva": 0.00,
    "otherFees": 0.00,
    "totalFrictionCost": 0.25,
    "finalNetCost": 104.01,
    "adjustedUnitPrice": 109.06094000,
    "reviewStatus": "OK"             // "OK" | "REQUIERE_REVISION" | null
  }
}
```

### `frictionBreakdown` — reglas de render

| Campo | Siempre presente | Significado |
|---|---|---|
| `grossAmount` | ✅ | Costo bruto (cantidad × precio) |
| `totalFrictionCost` | ✅ | Fricción total (comisión + IVA + otros) |
| `finalNetCost` | ✅ | Costo/monto neto real (bruto ± fricción) |
| `adjustedUnitPrice` | ✅ (null si qty = 0) | Precio unitario **efectivo** (incluye fricción) |
| `brokerCommission` | ⚠️ null en altas manuales | Comisión de corretaje |
| `brokerIva` | ⚠️ null en altas manuales | IVA (solo MXN; 0 en USD) |
| `otherFees` | ⚠️ null en altas manuales | Fees regulatorios/otros (DriveWealth) |
| `reviewStatus` | ⚠️ null si no evaluado | `OK` \| `REQUIERE_REVISION` |

**Render condicional:** los 4 campos "finos" (`brokerCommission`, `brokerIva`, `otherFees`,
`reviewStatus`) son `null` en transacciones creadas a mano (sin comprobante). Muestra el desglose fino
solo cuando `brokerCommission != null`. Los 4 derivados siempre vienen → puedes mostrar
gross/fricción/neto/precio efectivo para **cualquier** transacción.

**`reviewStatus === "REQUIERE_REVISION"`:** el neto calculado no cuadró con lo reportado por el broker
(diferencia > tolerancia). Muestra un **badge de advertencia** ("Requiere revisión") para que el
usuario verifique el comprobante. `OK` o `null` = sin alerta.

**Sugerencia de UI (tarjeta de auditoría):**
```
Bruto            $103.76
+ Comisión       $0.25
+ IVA            $0.00
+ Otros fees     $0.00
─────────────────────
Costo neto real  $104.01
Precio efectivo  $109.06 / u
```

---

## 4. Importar comprobantes (PDF) — flujo asíncrono

El import es **asíncrono**: subes → recibes un `jobId` por archivo (HTTP 202) → haces **polling** del
estado hasta `COMPLETED` o `DEAD_LETTER`.

### 4.1 Subida con auto-detección de broker (recomendado)

```
POST /api/v1/me/broker/gbm/import
Content-Type: multipart/form-data
Body: files[]  (uno o varios PDF)
→ 202 Accepted
```

El backend detecta solo el tipo de documento (estado GBM mensual, comprobante GBM renta variable, o
confirmación DriveWealth) inspeccionando el contenido. **El frontend no necesita declarar el broker.**

Respuesta (array, un elemento por archivo — `StatementImportJobResponse`):

```jsonc
[
  {
    "jobId": "…",
    "fileName": "fmty.pdf",
    "jobType": "AUTO_DETECT",
    "status": "QUEUED",
    "result": null,
    "errorMessage": null,
    "attemptCount": 0,
    "createdAt": "2026-01-02T09:47:00Z",
    "completedAt": null
  }
]
```

### 4.2 Endpoints explícitos (opcionales, si prefieres forzar el tipo)

```
POST /api/v1/me/broker/gbm/statements                 (1 archivo)  → estado mensual GBM
POST /api/v1/me/broker/gbm/drivewealth-confirmations  (files[])    → confirmaciones DriveWealth
```

### 4.3 Polling del estado del job

```
GET /api/v1/me/broker/gbm/import-jobs/{jobId}
```

Estados (`status`): `QUEUED` → `PROCESSING` → `COMPLETED` | `DEAD_LETTER`.
(Puede volver de `PROCESSING` a `QUEUED` en errores transitorios; ver `attemptCount`.)

**Completado con éxito:**
```jsonc
{
  "jobId": "…",
  "status": "COMPLETED",
  "result": {
    "fileName": "fmty.pdf",
    "accepted": 1,     // transacciones creadas
    "duplicate": 0,    // ya existentes (omitidas por idempotencia)
    "skipped": 0,
    "rejected": 0,     // filas rechazadas
    "messages": []
  },
  "errorMessage": null,
  "attemptCount": 0,
  "completedAt": "2026-01-02T09:47:05Z"
}
```

**Fallido (documento no reconocido / ilegible):**
```jsonc
{
  "status": "DEAD_LETTER",
  "result": null,
  "errorMessage": "No se pudo detectar el broker/tipo del documento subido",
  "attemptCount": 0
}
```
Muestra `errorMessage` al usuario. Un `DEAD_LETTER` se puede reintentar manualmente:

```
POST /api/v1/me/broker/gbm/import-jobs/{jobId}/retry
```

### 4.4 Listar cargas recientes

```
GET /api/v1/me/broker/gbm/import-jobs      → últimos 20 jobs, más reciente primero
```

**Sugerencia de UX del flujo:** subir → mostrar spinner por archivo → poll cada ~2 s → al `COMPLETED`
mostrar "N transacciones importadas" (`result.accepted`); al `DEAD_LETTER` mostrar el `errorMessage` y
ofrecer botón "Reintentar".

---

## 5. Errores (RFC 9457 Problem Detail)

Los errores llegan como `application/problem+json`:
```jsonc
{
  "type": "…", "title": "…", "status": 400,
  "detail": "…", "instance": "…", "errorCode": "…", "traceId": "…"
}
```
Códigos usados: 400 (payload inválido), 401 (sin token), 403 (sin permiso), 404, 409 (conflicto de
negocio, p. ej. reintentar un job que no está en `DEAD_LETTER`), 429 (rate limit).

---

## 6. Notas

- **`adjustedUnitPrice`** es el precio unitario **efectivo** incluyendo fricción — útil para mostrar
  el costo real por acción, distinto del `pricePerUnit` de mercado.
- **Precisión:** los montos son decimales (BigDecimal serializado como número). No uses `float`;
  maneja con librería decimal o strings para no perder precisión al mostrar dinero.
- **Tasas de fricción (comisión 0.25% / IVA 16% / tolerancia):** son configuración de **backend**
  (`friction.gbm.*`), no las manda el frontend. Solo relevante saber que existen.
- **Pendiente backend (no bloquea al frontend):** calibrar los regex del parser GBM renta variable
  con un PDF real. El contrato de API no cambia por eso.
