#!/usr/bin/env bash
#
# Smoke test de proveedores externos de datos de cripto.
#
# Por que existe: el bloqueo HTTP 451 de Binance/Bybit depende de la IP de SALIDA
# (geobloqueo de compliance US). Desde Mexico (dev) nunca se reproduce; desde un
# runner US (GitHub Actions hospedado = Azure US) si. Este script valida, desde
# donde corra, que los proveedores de los que depende produccion responden, y
# vigila como canario los que estan gateados por region (ADR-0021).
#
# Criterio de fallo (POR PROVEEDOR, no "cualquier 451"):
#   - REQUIRED : prod depende de ellos -> cualquier status != 200 rompe el pipeline.
#   - CANARY   : bloqueados-en-US (gateados en prod) -> 451 es ESPERADO (ok);
#                un 200 inesperado solo avisa (geobloqueo levantado / runner no-US).
#
# Uso: ci/smoke-external-providers.sh
# Sale con codigo != 0 solo si falla al menos un REQUIRED.

set -u

MAX_TIME="${SMOKE_MAX_TIME:-15}"
RETRIES="${SMOKE_RETRIES:-2}"

# nombre|url|status_esperado
REQUIRED=(
  "Coinbase|https://api.exchange.coinbase.com/products/BTC-USD/ticker|200"
  "Kraken|https://api.kraken.com/0/public/Time|200"
  "CoinGecko|https://api.coingecko.com/api/v3/ping|200"
)

# nombre|url|status_esperado_desde_US
CANARY=(
  "Binance|https://api.binance.com/api/v3/ping|451"
  "BinanceFutures|https://fapi.binance.com/fapi/v1/ping|451"
  "Bybit|https://api.bybit.com/v5/market/time|451"
)

http_code() {
  curl -s -o /dev/null -w '%{http_code}' \
    --max-time "$MAX_TIME" --retry "$RETRIES" --retry-connrefused \
    "$1" 2>/dev/null || echo "000"
}

printf '%-16s %-7s %-9s %s\n' "PROVEEDOR" "HTTP" "ESPERADO" "RESULTADO"
printf '%-16s %-7s %-9s %s\n' "----------------" "-------" "---------" "---------"

failures=0

for row in "${REQUIRED[@]}"; do
  IFS='|' read -r name url expected <<<"$row"
  code="$(http_code "$url")"
  if [ "$code" = "$expected" ]; then
    printf '%-16s %-7s %-9s %s\n' "$name" "$code" "$expected" "OK"
  else
    printf '%-16s %-7s %-9s %s\n' "$name" "$code" "$expected" "FALLA (requerido)"
    failures=$((failures + 1))
  fi
done

for row in "${CANARY[@]}"; do
  IFS='|' read -r name url expected <<<"$row"
  code="$(http_code "$url")"
  if [ "$code" = "$expected" ]; then
    printf '%-16s %-7s %-9s %s\n' "$name" "$code" "$expected" "ok (canario, esperado)"
  else
    printf '%-16s %-7s %-9s %s\n' "$name" "$code" "$expected" "AVISO: status inesperado"
  fi
done

echo ""
if [ "$failures" -gt 0 ]; then
  echo "SMOKE FALLIDO: $failures proveedor(es) REQUERIDO(s) no respondieron 200."
  echo "Prod depende de ellos; revisar geobloqueo/credencial/contrato antes de desplegar."
  exit 1
fi

echo "SMOKE OK: todos los proveedores requeridos respondieron como se espera."
exit 0
