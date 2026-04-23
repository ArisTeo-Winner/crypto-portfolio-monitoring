# Database production checklist

Run this checklist before promoting the backend to staging or production.

## Commands

Render the production compose:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env config
```

Run Flyway migration tests locally:

```powershell
.\mvnw -q "-Dtest=com.mx.cryptomonitor.integration.config.FlywayBaselineMigrationIT,com.mx.cryptomonitor.integration.config.FlywayConvergenceMigrationIT" test
```

Run DB preflight against staging/production:

```powershell
$env:PGPASSWORD=$env:SPRING_DATASOURCE_PASS
psql -h <db-host> -U $env:SPRING_DATASOURCE_USER -d <db-name> -f deployment/postgres/production-preflight.sql
```

## Go/no-go checklist

| Item | Gate | Verification |
| --- | --- | --- |
| Base de datos creada con encoding definido | Must pass | `production-preflight.sql` must return `database_encoding_utf8 = PASS`. |
| Base de datos creada con timezone definido | Must pass | `production-preflight.sql` must return `database_timezone_utc = PASS`. |
| Roles DB definidos | Must pass | Use `deployment/postgres/production-roles-template.sql` or equivalent managed-cloud roles. |
| Permisos mínimos otorgados | Must pass | Preflight must return `runtime_role_not_superuser = PASS` and `runtime_role_no_schema_create = PASS` when run as the app user. |
| Esquema limpio, sin columnas basura | Must pass | Preflight must return `schema_no_unexpected_columns = PASS`. |
| Columnas requeridas presentes | Must pass | Preflight must return `schema_required_columns_present = PASS`. |
| Constraints revisadas | Must pass | Preflight must return `constraints_reviewed = PASS`. |
| Índices creados | Must pass | Preflight must return `indexes_created = PASS`. |
| Triggers auditados | Must pass | Preflight must return `triggers_audited = PASS`. Current expected state: no DB triggers. |
| Migraciones Flyway completas | Must pass | Preflight must return `flyway_schema_history_present = PASS` and `flyway_migrations_complete = PASS`. |
| Datos semilla cargados | Must pass | Preflight must return `seed_roles_loaded = PASS` and `seed_permissions_loaded = PASS`. |
| Usuario admin inicial creado | Must pass | Set `BOOTSTRAP_ADMIN_ENABLED=true` only for first startup, then verify `admin_user_exists = PASS` and disable it again. |
| Backups definidos | Manual evidence required | Backup schedule, retention, encryption and owner must be documented before production. |
| Estrategia de restore probada | Manual evidence required | Restore drill must be executed and documented with RPO/RTO evidence. |
| Entorno prod sin `ddl-auto=update` | Must pass | `application-prod.properties` uses `spring.jpa.hibernate.ddl-auto=validate`. |
| Conexiones y pool definidos | Must pass | `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `POSTGRES_MAX_CONNECTIONS` set and sized with `(replicas * pool) + margin <= max_connections`. |
| Logs y auditoría validados | Must pass | `audit_log_table_ready = PASS`; execute login/register/delete/update flows and confirm rows in `audit_logs`. |

## Admin bootstrap procedure

Set these variables only for the first controlled startup:

```properties
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=<strong-secret-from-secret-manager>
```

Start the app, confirm the admin exists with `production-preflight.sql`, then disable it:

```properties
BOOTSTRAP_ADMIN_ENABLED=false
```

Do not keep the bootstrap password in `.env`, source control, container images, logs, or shell history.

## Final decision rule

Staging can proceed when every `Must pass` row is green and every manual-evidence row has an owner and date.

Production cannot proceed until backup and restore evidence exists.
