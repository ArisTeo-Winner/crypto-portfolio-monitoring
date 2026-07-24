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

## Colección principal — carpetas `05 - Assets` y `07 - Market Data MXN / Banxico`

El archivo `crypto-portfolio-monitoring.postman_collection.json` (colección
principal, distinta de la de arriba) se extendió con requests para el catálogo
filtrado por tipo y para la valuación a mercado (mark-to-market) de CETES:

- **`05 - Assets`**: `GET /api/v1/assets?type=STOCK|ETF|GOVERNMENT_BOND`, el
  catálogo completo sin filtro, los casos negativos (`type` inválido, `limit`
  fuera de rango) y `GET /api/v1/assets/popular` con verificación de logos
  reales de Finnhub (incluido GOOG/GOOGL).
- **`07 - Market Data MXN / Banxico`**: curva de tasas CETES de Banxico
  (`GET /api/v1/marketdata/banxico/cetes/curve`) y el mark-to-market de una
  posición CETES viva (`GET /api/v1/portfolio/cetes/{transactionId}/mark-to-market`),
  incluyendo los casos 404 (transacción ajena/inexistente) y 400 (transacción
  que no es un bono gubernamental).

### Requisitos adicionales para que estas carpetas pasen

- **`BANXICO_TOKEN`** configurado en el backend (variable de entorno en
  Docker) para que la curva de tasas CETES devuelva datos reales.
- **`FINNHUB_TOKEN`** configurado para que `GET /api/v1/assets/popular`
  devuelva logos reales de STOCK (`logoUrl` no `null`), incluido GOOG/GOOGL.
- El **sync del catálogo** (`forceFullSync`, al arranque o vía el job
  semanal) debe haber corrido al menos una vez para que los logos y el market
  cap de STOCK estén poblados antes de correr `05 - Assets`.

### Orden de ejecución recomendado

1. `00 - Auth & Users` → `Register user` y `Login` (captura `accessToken`).
2. (Opcional) Esperar a que el sync del catálogo haya corrido al menos una
   vez, o dispararlo manualmente, antes de correr `05 - Assets`.
3. `05 - Assets` — catálogo por tipo y `popular` (verifica logos Finnhub).
4. `07 - Market Data MXN / Banxico`, en orden:
   1. `Banxico CETES rate curve` (pública).
   2. `Buy CETES91 (live position, for MtM)` — crea la posición viva y guarda
      `cetes_tx_id`.
   3. `CETES mark-to-market (live position)` — usa `cetes_tx_id`.
   4. `CETES mark-to-market - not found (404)`.
   5. `Buy STOCK transaction (helper for MtM 400 test)` — guarda
      `stock_tx_id`.
   6. `CETES mark-to-market - not a government bond (400)` — usa
      `stock_tx_id`.
