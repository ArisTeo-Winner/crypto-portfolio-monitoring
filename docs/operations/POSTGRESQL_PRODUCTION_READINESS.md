# PostgreSQL production readiness

This document defines the minimum database posture for staging and production.

## Runtime configuration applied in the repository

- Hibernate schema mutation is disabled with `spring.jpa.hibernate.ddl-auto=validate`.
- Flyway is enabled and validates migrations before startup.
- Flyway clean is disabled.
- The `prod` profile disables automatic Flyway baselining by default.
- HikariCP pool limits are controlled by environment variables.
- `/actuator/health/readiness` includes `db` and `redis`.
- `docker-compose.prod.yml` keeps PostgreSQL and Redis private inside the Docker network.
- Redis requires a password in `docker-compose.prod.yml`.
- Swagger/OpenAPI UI is disabled by default in the `prod` profile.
- DB roles and grants are templated in `deployment/postgres/production-roles-template.sql`.
- DB go/no-go checks are automated in `deployment/postgres/production-preflight.sql`.
- The final checklist is documented in `docs/operations/DATABASE_PRODUCTION_CHECKLIST.md`.

## Required environment variables

Minimum database variables:

```properties
DB_NAME=crypto_portfolio
DB_USER=crypto_app
DB_PASS=change-me
SPRING_DATASOURCE_URL=jdbc:postgresql://java_db:5432/crypto_portfolio
SPRING_DATASOURCE_USER=crypto_app
SPRING_DATASOURCE_PASS=change-me
```

For cloud databases, prefer TLS:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.com:5432/crypto_portfolio?sslmode=verify-full
```

Minimum Redis variable:

```properties
REDIS_PASSWORD=change-me
```

Recommended pool variables:

```properties
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
DB_CONNECTION_TIMEOUT_MS=30000
DB_VALIDATION_TIMEOUT_MS=5000
DB_IDLE_TIMEOUT_MS=600000
DB_MAX_LIFETIME_MS=1800000
DB_LEAK_DETECTION_THRESHOLD_MS=0
POSTGRES_MAX_CONNECTIONS=100
```

Pool sizing rule:

```text
(app replicas * DB_POOL_MAX_SIZE) + admin margin <= POSTGRES_MAX_CONNECTIONS
```

Example:

```text
3 replicas * 10 pool connections + 20 admin/reporting margin <= 100 max connections
```

## Staging or production compose

Build the jar first:

```powershell
.\mvnw -B -DskipTests package
```

Start the production-shaped stack:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Check containers:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env ps
```

Check readiness:

```powershell
curl http://localhost:8080/actuator/health/readiness
```

## Flyway migration policy

For new databases:

- Keep `FLYWAY_BASELINE_ON_MIGRATE=false`.
- Let Flyway apply `V1` and later migrations from an empty schema.

For existing legacy databases:

- Do not enable automatic baseline permanently.
- Run a supervised one-time convergence window.
- Set `FLYWAY_BASELINE_ON_MIGRATE=true` only during that controlled import if the schema has no `flyway_schema_history`.
- Return it to `false` immediately after the migration history is established.

Never run `flyway.clean` against staging or production.

## Preflight SQL checks before migrating existing data

Check duplicate emails before enforcing case-insensitive uniqueness:

```sql
select lower(email) as normalized_email, count(*)
from users
group by lower(email)
having count(*) > 1;
```

Check orphan portfolio projections:

```sql
select p.user_id, p.asset_symbol, p.asset_type
from portfolio_entry p
where not exists (
  select 1
  from transaction t
  where t.user_id = p.user_id
    and t.asset_symbol = p.asset_symbol
    and t.asset_type = p.asset_type
);
```

If rows are returned, reconcile through the official application flow or a reviewed data repair script. Do not manually delete only one side of the `transaction` and `portfolio_entry` relationship.

## Backup and recovery requirements

The repository cannot guarantee production safety without an external backup strategy.

Required before production:

- Daily logical backups with `pg_dump`.
- Continuous WAL archiving or managed PITR if available.
- Encrypted backup storage.
- Restore drill before launch.
- Documented RPO and RTO.
- Retention policy by environment.

Recommended minimum:

```text
staging: daily backup, 7 day retention
production: PITR, daily logical backup, 30 day retention, monthly restore drill
```

## Security requirements

- Do not expose PostgreSQL port `5432` publicly.
- Do not expose Redis port `6379` publicly.
- Use strong generated passwords for database and Redis.
- Use TLS for managed/cloud PostgreSQL.
- Use separate roles when possible:
  - migration role with DDL privileges
  - runtime role with only required DML privileges
- Keep secrets outside the image and source control.

## Monitoring requirements

Before production, dashboards should track:

- PostgreSQL connections used vs max.
- Slow queries using `log_min_duration_statement`.
- Transaction locks and blocked queries.
- Database CPU, memory, disk usage, and disk growth.
- Hikari active, idle, pending, and timeout metrics.
- Redis memory, evictions, latency, and connection failures.
- `/actuator/health/readiness` status.

## Current production readiness status

Staging-ready:

- Yes, if `docker-compose.prod.yml` or equivalent private networking is used.
- Yes, if migrations pass from a clean PostgreSQL database.
- Yes, if required secrets are injected by environment.

Production-ready:

- Not fully by code alone.
- Production still requires backup/PITR, restore validation, external monitoring, TLS configuration, and a deployment platform with controlled secrets.
