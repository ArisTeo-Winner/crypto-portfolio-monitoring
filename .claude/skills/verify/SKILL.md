---
name: verify
description: How to build, run, and drive this Spring Boot app locally to verify a change end-to-end (not via tests).
---

# Verifying crypto-portfolio-monitoring locally

## Bring up infra

```bash
docker-compose up -d java_db redis   # postgres:16-alpine + redis:7, healthcheck ~10s
```

`.env` at repo root already has local values (`DB_HOST=localhost`, `SPRING_DATA_REDIS_HOST=localhost`,
JWT keys, all market-data API keys). Contains real secrets — never print its contents into a report.

## Run the app

```bash
set -a && source .env && set +a
./mvnw clean compile spring-boot:run
```

**Do not run `./mvnw spring-boot:run` alone.** Invoked directly (not via a phase), Maven does not
recompile first — a stale/partial `target/classes` from a previous interrupted build causes
`NoClassDefFoundError` on boot even though the source is fine. Always `compile` (or `clean compile`)
in the same invocation.

App logs to stdout with `INFO`/`WARN`/`ERROR` — no separate log file. Boot success looks like:
`Started CryptoPortfolioMonitoringApplication in N seconds`. Flyway migration lines show each
version applied — useful to confirm new migrations ran (`Successfully applied N migrations`).

Springdoc/Swagger (`/v3/api-docs`, `/swagger-ui`) is **disabled by default** locally
(`springdoc.api-docs.enabled=${SPRINGDOC_API_DOCS_ENABLED:false}`) — requesting it 404s
("No static resource..."), not a real endpoint failure.

## Drive the API

1. Register: `POST /api/v1/users/register` — `firstName`/`lastName` must be **letters only**
   (no digits) or validation fails with 400.
2. Login: `POST /api/v1/auth/login` → `accessToken`. **Expires in 15 minutes**
   (`JWT_ACCESS_EXPIRATION=900000`) — re-login if a long verification session goes stale
   (shows up as an unexpected 401 on a previously-working call).
3. Use `Authorization: Bearer <token>` on `/api/v1/me/**`.

### statementimport module specifically

Real sample PDFs live at `src/test/resources/statementimport/gbm-statement-sample.pdf` and
`drivewealth-confirmation-sample.pdf` — use these for upload tests instead of synthetic files.

```bash
curl -s -X POST http://localhost:8080/api/v1/me/broker/gbm/statements \
  -H "Authorization: Bearer $TOKEN" -F "file=@path/to/sample.pdf"
# -> 202 { jobId, status: QUEUED }
curl -s http://localhost:8080/api/v1/me/broker/gbm/import-jobs/$JOBID -H "Authorization: Bearer $TOKEN"
# poll every ~2s (worker poll-interval) until status is COMPLETED or DEAD_LETTER
```

Worth probing: re-upload the same PDF (should report `duplicate`, not create new transactions),
swap the two sample PDFs into each other's endpoint (content-sniffing → `DEAD_LETTER`, no retry),
upload a non-PDF file (`DEAD_LETTER`: "El archivo no contiene un encabezado PDF valido"), retry a
non-`DEAD_LETTER` job (409), retry a `DEAD_LETTER` job (requeues, `attemptCount` does not reset),
and cross-user access to another user's job (404, not 403 — doesn't leak existence).

## Shell notes

Bash tool here is Git Bash on Windows. `curl -F "file=@$LONG_TMP_PATH_WITH_MANY_DASHES"` has
intermittently produced no output/silent failure in this environment — if a curl call to a temp
scratchpad path returns nothing, `cd` into that directory first and use a relative filename instead
of a long absolute path.
