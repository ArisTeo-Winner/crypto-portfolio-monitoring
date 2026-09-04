#!/usr/bin/env bash
# =============================================================================
# upload-sbom.sh — sube el SBOM CycloneDX (target/bom.json) a Dependency-Track.
# -----------------------------------------------------------------------------
# Uso:
#   DTRACK_API_KEY=odt_xxx ./scripts/upload-sbom.sh
#
# Variables de entorno:
#   DTRACK_URL           URL base del API (default: http://localhost:8081)
#   DTRACK_API_KEY       (OBLIGATORIA) API key con permiso BOM_UPLOAD
#   BOM_FILE             ruta del SBOM (default: target/bom.json)
#   Modo A -> por UUID de proyecto existente:
#     DTRACK_PROJECT_UUID   UUID del proyecto en Dependency-Track
#   Modo B -> auto-crear/actualizar por nombre+version:
#     DTRACK_PROJECT_NAME     (default: crypto-portfolio-monitoring)
#     DTRACK_PROJECT_VERSION  (default: git short SHA o 0.0.1-SNAPSHOT)
#
# Codigos de salida:
#   0  subida correcta (HTTP 2xx)
#   1  error de uso / falta configuracion / archivo ausente
#   2  error HTTP del servidor Dependency-Track (>= 400)
# =============================================================================
set -euo pipefail

DTRACK_URL="${DTRACK_URL:-http://localhost:8081}"
BOM_FILE="${BOM_FILE:-target/bom.json}"
DTRACK_PROJECT_NAME="${DTRACK_PROJECT_NAME:-crypto-portfolio-monitoring}"

if [ -z "${DTRACK_API_KEY:-}" ]; then
  echo "ERROR: DTRACK_API_KEY no esta definida." >&2
  exit 1
fi

if [ ! -f "$BOM_FILE" ]; then
  echo "ERROR: SBOM no encontrado en '$BOM_FILE'. Ejecuta primero: mvn -DskipTests package" >&2
  exit 1
fi

# Version por defecto: short SHA de git si es posible, si no el fallback.
if [ -z "${DTRACK_PROJECT_VERSION:-}" ]; then
  if command -v git >/dev/null 2>&1 && git rev-parse --short HEAD >/dev/null 2>&1; then
    DTRACK_PROJECT_VERSION="$(git rev-parse --short HEAD)"
  else
    DTRACK_PROJECT_VERSION="0.0.1-SNAPSHOT"
  fi
fi

echo "Subiendo '$BOM_FILE' a $DTRACK_URL/api/v1/bom ..."

# Construir argumentos: por UUID (modo A) o auto-crear por nombre/version (modo B).
form_args=(-F "bom=@${BOM_FILE}")
if [ -n "${DTRACK_PROJECT_UUID:-}" ]; then
  echo "Modo: proyecto por UUID (${DTRACK_PROJECT_UUID})"
  form_args+=(-F "project=${DTRACK_PROJECT_UUID}")
else
  echo "Modo: auto-crear/actualizar '${DTRACK_PROJECT_NAME}' v${DTRACK_PROJECT_VERSION}"
  form_args+=(-F "autoCreate=true" \
              -F "projectName=${DTRACK_PROJECT_NAME}" \
              -F "projectVersion=${DTRACK_PROJECT_VERSION}")
fi

# -s silencioso, -w captura el codigo HTTP en la ultima linea, cuerpo va a stdout.
http_body="$(mktemp)"
trap 'rm -f "$http_body"' EXIT

http_code="$(curl -sS -o "$http_body" -w '%{http_code}' \
  -X POST "${DTRACK_URL}/api/v1/bom" \
  -H "X-Api-Key: ${DTRACK_API_KEY}" \
  -H "Accept: application/json" \
  "${form_args[@]}")" || {
    echo "ERROR: fallo de red al contactar Dependency-Track." >&2
    exit 2
  }

echo "HTTP ${http_code}"
cat "$http_body"; echo

if [ "$http_code" -ge 400 ]; then
  echo "ERROR: Dependency-Track respondio ${http_code}." >&2
  exit 2
fi

echo "SBOM subido correctamente."
