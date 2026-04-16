#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

ffuf \
  -w ci/security/payloads/user-emails.txt:EMAIL \
  -w ci/security/payloads/passwords.txt:PASS \
  -u "${BASE_URL}/api/v1/auth/login" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"EMAIL","password":"PASS"}' \
  -mc 200,401,429 \
  -rate 10 \
  -timeout 10 \
  -of json \
  -o target/security/ffuf-login-report.json
