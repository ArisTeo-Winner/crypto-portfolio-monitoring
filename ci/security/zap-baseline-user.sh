#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
REPORT_DIR="target/security/zap"
mkdir -p "${REPORT_DIR}"

docker run --rm \
  -v "$(pwd):/zap/wrk:rw" \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py \
    -t "${BASE_URL}/v3/api-docs" \
    -f openapi \
    -J "${REPORT_DIR}/zap-user-api-report.json" \
    -r "${REPORT_DIR}/zap-user-api-report.html" \
    -I
