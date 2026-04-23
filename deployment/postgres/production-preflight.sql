-- Production database preflight checks.
--
-- Run as the application runtime DB user when possible:
-- PGPASSWORD=<password> psql -h <host> -U <app_role> -d <db_name> -f deployment/postgres/production-preflight.sql

\set ON_ERROR_STOP on
\pset pager off

SELECT 'database_encoding_utf8' AS check_name,
       CASE WHEN pg_encoding_to_char(encoding) = 'UTF8' THEN 'PASS' ELSE 'FAIL' END AS status,
       pg_encoding_to_char(encoding) AS details
FROM pg_database
WHERE datname = current_database()
UNION ALL
SELECT 'database_timezone_utc',
       CASE WHEN current_setting('TimeZone') IN ('UTC', 'Etc/UTC') THEN 'PASS' ELSE 'FAIL' END,
       current_setting('TimeZone')
UNION ALL
SELECT 'runtime_role_not_superuser',
       CASE
           WHEN NOT rolsuper
            AND NOT rolcreatedb
            AND NOT rolcreaterole
            AND NOT rolreplication
            AND NOT rolbypassrls THEN 'PASS'
           ELSE 'FAIL'
       END,
       current_user
FROM pg_roles
WHERE rolname = current_user
UNION ALL
SELECT 'runtime_role_no_schema_create',
       CASE WHEN NOT has_schema_privilege(current_user, 'public', 'CREATE') THEN 'PASS' ELSE 'FAIL' END,
       current_user || ' CREATE on public=' || has_schema_privilege(current_user, 'public', 'CREATE')::text
UNION ALL
SELECT 'flyway_schema_history_present',
       CASE WHEN to_regclass('public.flyway_schema_history') IS NOT NULL THEN 'PASS' ELSE 'FAIL' END,
       COALESCE(to_regclass('public.flyway_schema_history')::text, 'missing');

WITH expected(version) AS (
    VALUES ('1'), ('2'), ('3'), ('2026.02.18.01')
),
applied AS (
    SELECT version
    FROM flyway_schema_history
    WHERE success = true
)
SELECT 'flyway_migrations_complete' AS check_name,
       CASE WHEN COUNT(missing.version) = 0 THEN 'PASS' ELSE 'FAIL' END AS status,
       COALESCE(string_agg(missing.version, ', '), 'all expected migrations applied') AS details
FROM (
    SELECT version FROM expected
    EXCEPT
    SELECT version FROM applied
) missing;

WITH expected(table_name, column_name) AS (
    VALUES
        ('users','id'), ('users','username'), ('users','email'), ('users','password_hash'),
        ('users','first_name'), ('users','last_name'), ('users','phone_number'), ('users','address'),
        ('users','city'), ('users','state'), ('users','postal_code'), ('users','country'),
        ('users','date_of_birth'), ('users','profile_picture'), ('users','bio'), ('users','active'),
        ('users','created_at'), ('users','updated_at'), ('users','last_login'),
        ('roles','id'), ('roles','name'), ('roles','description'),
        ('permissions','id'), ('permissions','name'), ('permissions','code'), ('permissions','description'),
        ('user_roles','user_id'), ('user_roles','role_id'),
        ('role_permissions','role_id'), ('role_permissions','permission_id'),
        ('refresh_tokens','id'), ('refresh_tokens','user_id'), ('refresh_tokens','refresh_token'),
        ('refresh_tokens','created_at'), ('refresh_tokens','expires_at'), ('refresh_tokens','last_used_at'),
        ('refresh_tokens','revoked'), ('refresh_tokens','ip_address'), ('refresh_tokens','user_agent'),
        ('refresh_tokens','revoked_at'),
        ('sessions','session_id'), ('sessions','user_id'), ('sessions','refresh_token_id'),
        ('sessions','login_time'), ('sessions','logout_time'), ('sessions','is_active'),
        ('audit_logs','log_id'), ('audit_logs','user_id'), ('audit_logs','event_type'),
        ('audit_logs','description'), ('audit_logs','ip_address'), ('audit_logs','user_agent'),
        ('audit_logs','event_timestamp'),
        ('user_auth_providers','id'), ('user_auth_providers','user_id'), ('user_auth_providers','auth_provider'),
        ('user_auth_providers','provider_id'), ('user_auth_providers','provider_email'),
        ('user_auth_providers','created_at'), ('user_auth_providers','last_login'),
        ('portfolio_entry','portfolio_entry_id'), ('portfolio_entry','user_id'),
        ('portfolio_entry','asset_symbol'), ('portfolio_entry','asset_type'),
        ('portfolio_entry','total_quantity'), ('portfolio_entry','total_invested'),
        ('portfolio_entry','average_price_per_unit'), ('portfolio_entry','last_transaction_price'),
        ('portfolio_entry','current_value'), ('portfolio_entry','total_profit_loss'),
        ('portfolio_entry','last_updated'), ('portfolio_entry','created_at'),
        ('portfolio_entry','updated_at'), ('portfolio_entry','version'),
        ('transaction','transaction_id'), ('transaction','user_id'), ('transaction','portfolio_entry_id'),
        ('transaction','asset_symbol'), ('transaction','asset_type'), ('transaction','transaction_type'),
        ('transaction','transfer_type'), ('transaction','quantity'), ('transaction','price_per_unit'),
        ('transaction','total_value'), ('transaction','transaction_date'), ('transaction','fee'),
        ('transaction','notes'), ('transaction','created_at'), ('transaction','updated_at'),
        ('transaction_idempotency_keys','record_id'), ('transaction_idempotency_keys','user_id'),
        ('transaction_idempotency_keys','operation_scope'), ('transaction_idempotency_keys','idempotency_key'),
        ('transaction_idempotency_keys','request_hash'), ('transaction_idempotency_keys','status'),
        ('transaction_idempotency_keys','result_transaction_id'), ('transaction_idempotency_keys','created_at'),
        ('transaction_idempotency_keys','completed_at'), ('transaction_idempotency_keys','expires_at')
),
actual AS (
    SELECT table_name, column_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name IN (SELECT DISTINCT table_name FROM expected)
),
unexpected AS (
    SELECT table_name, column_name FROM actual
    EXCEPT
    SELECT table_name, column_name FROM expected
)
SELECT 'schema_no_unexpected_columns' AS check_name,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS status,
       COALESCE(string_agg(table_name || '.' || column_name, ', '), 'no unexpected columns') AS details
FROM unexpected;

WITH expected(table_name, column_name) AS (
    VALUES
        ('users','id'), ('users','username'), ('users','email'), ('users','password_hash'),
        ('roles','id'), ('roles','name'), ('permissions','id'), ('permissions','code'),
        ('portfolio_entry','portfolio_entry_id'), ('portfolio_entry','asset_symbol'),
        ('transaction','transaction_id'), ('transaction','transaction_date'),
        ('transaction_idempotency_keys','record_id'), ('transaction_idempotency_keys','idempotency_key')
),
actual AS (
    SELECT table_name, column_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
),
missing AS (
    SELECT table_name, column_name FROM expected
    EXCEPT
    SELECT table_name, column_name FROM actual
)
SELECT 'schema_required_columns_present' AS check_name,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS status,
       COALESCE(string_agg(table_name || '.' || column_name, ', '), 'required columns present') AS details
FROM missing;

WITH expected(index_name) AS (
    VALUES
        ('uq_users_username'), ('uq_users_email_lower'), ('uq_roles_name'), ('uq_permissions_code'),
        ('idx_refresh_tokens_user_id'), ('idx_refresh_tokens_expires_at'),
        ('idx_sessions_user_id'), ('idx_sessions_active_user'),
        ('idx_audit_logs_user_timestamp'), ('idx_audit_logs_event_type_timestamp'),
        ('idx_user_auth_providers_lookup'), ('idx_portfolio_entry_asset_symbol'),
        ('idx_portfolio_entry_user_asset_type'), ('uq_portfolio_entry_user_symbol'),
        ('idx_transaction_user_date'),
        ('idx_transaction_user_asset_symbol_date'), ('idx_transaction_user_asset_type_date'),
        ('idx_transaction_user_type_date'), ('idx_transaction_portfolio_entry_id'),
        ('idx_transaction_idempotency_expires_at')
),
actual AS (
    SELECT indexname AS index_name
    FROM pg_indexes
    WHERE schemaname = 'public'
),
missing AS (
    SELECT index_name FROM expected
    EXCEPT
    SELECT index_name FROM actual
)
SELECT 'indexes_created' AS check_name,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS status,
       COALESCE(string_agg(index_name, ', '), 'expected indexes present') AS details
FROM missing;

WITH expected(constraint_name) AS (
    VALUES
        ('fk_user_roles_user'), ('fk_user_roles_role'),
        ('fk_role_permissions_role'), ('fk_role_permissions_permission'),
        ('fk_refresh_tokens_user'), ('fk_sessions_user'), ('fk_audit_logs_user'),
        ('uq_user_provider'), ('fk_user_auth_providers_user'), ('uq_portfolio_entry_user_symbol'),
        ('fk_transaction_user'),
        ('uq_transaction_idempotency_user_scope_key'), ('audit_logs_event_type_check')
),
actual AS (
    SELECT conname AS constraint_name
    FROM pg_constraint
    WHERE connamespace = 'public'::regnamespace
),
missing AS (
    SELECT constraint_name FROM expected
    EXCEPT
    SELECT constraint_name FROM actual
)
SELECT 'constraints_reviewed' AS check_name,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS status,
       COALESCE(string_agg(constraint_name, ', '), 'expected constraints present') AS details
FROM missing;

SELECT 'triggers_audited' AS check_name,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS status,
       COALESCE(string_agg(trigger_name || ' on ' || event_object_table, ', '), 'no application triggers expected') AS details
FROM information_schema.triggers
WHERE trigger_schema = 'public'
  AND event_object_table IN (
      'users', 'roles', 'permissions', 'user_roles', 'role_permissions', 'refresh_tokens',
      'sessions', 'audit_logs', 'user_auth_providers', 'portfolio_entry', 'transaction',
      'transaction_idempotency_keys'
  );

SELECT 'seed_roles_loaded' AS check_name,
       CASE
           WHEN EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN')
            AND EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER')
            AND EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_VIEWER') THEN 'PASS'
           ELSE 'FAIL'
       END AS status,
       'roles=' || COUNT(*)::text AS details
FROM roles
UNION ALL
SELECT 'seed_permissions_loaded',
       CASE WHEN COUNT(*) >= 12 THEN 'PASS' ELSE 'FAIL' END,
       'permissions=' || COUNT(*)::text
FROM permissions
UNION ALL
SELECT 'admin_user_exists',
       CASE WHEN COUNT(*) >= 1 THEN 'PASS' ELSE 'FAIL' END,
       'admin_users=' || COUNT(*)::text
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
WHERE r.name = 'ROLE_ADMIN'
UNION ALL
SELECT 'audit_log_table_ready',
       CASE WHEN to_regclass('public.audit_logs') IS NOT NULL THEN 'PASS' ELSE 'FAIL' END,
       COALESCE(to_regclass('public.audit_logs')::text, 'missing');
