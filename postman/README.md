# Postman — Nuevos Endpoints (Catalog / Transaction / BMV)

Colección para probar manualmente los endpoints nuevos de los módulos
`transaction` (PROMPT A), `asset` (PROMPT B) y `marketdata` (PROMPT C),
contra el backend Spring Boot corriendo en Docker (`http://localhost:8080`).

## Archivos

- `CryptoPortfolio_NuevosEndpoints.postman_collection.json` — colección v2.1.0
- `CryptoPortfolio_Local.postman_environment.json` — environment "CryptoPortfolio Local"

## Uso

1. **Importar** en Postman: `File → Import` y arrastra ambos archivos
   (collection + environment).
2. **Seleccionar** el environment **"CryptoPortfolio Local"** en el selector
   superior derecho.
3. **Ejecutar la carpeta `0 - Auth` primero**:
   - `0.1 Register` genera un email único (`test+<timestamp>@example.com`) y
     lo persiste en `{{user_email}}`.
   - `0.2 Login` guarda automáticamente el `accessToken` en `{{access_token}}`.
   Las carpetas 2 y 3 dependen de ese token.
4. **Correr el resto** con `Run collection` (Collection Runner). Para la prueba
   de idempotencia `2.8`, ejecuta esa request **dos veces** con la misma
   `{{idem_key}}` (se fija automáticamente la primera vez) y verifica que el
   `transactionId` es idéntico.

## Requisitos

- Backend arriba en `http://localhost:8080` (Docker).
- **`DATABURSATIL_TOKEN`** configurado en el backend para que la **carpeta 3**
  (FX USD/MXN e histórico BMV) devuelva datos reales.

## Notas

- **Carpeta 1 (Catálogo)** es pública: no envía `Authorization`.
- **Carpetas 2 y 3** envían `Authorization: Bearer {{access_token}}`.
- El símbolo `*` de la BMV se URL-encodea como `%2A` en la ruta
  (`/bmv/historical/AAPL%2A`).
- Los POST de transacción/dividendo requieren el header `X-Idempotency-Key`
  (se genera con `{{$guid}}` salvo en la prueba de idempotencia 2.8).

## Validación opcional con Newman

Con el backend arriba:

```bash
newman run postman/CryptoPortfolio_NuevosEndpoints.postman_collection.json \
  -e postman/CryptoPortfolio_Local.postman_environment.json --bail
```
