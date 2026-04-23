-- Run with psql as a PostgreSQL administrator before the application starts.
--
-- Example:
-- psql -h <host> -U postgres \
--   -v db_name=crypto_portfolio \
--   -v migration_role=crypto_migrator \
--   -v migration_password='<strong-password>' \
--   -v app_role=crypto_app \
--   -v app_password='<strong-password>' \
--   -f deployment/postgres/production-roles-template.sql

\set ON_ERROR_STOP on

SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
    :'migration_role',
    :'migration_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migration_role')
\gexec

SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
    :'app_role',
    :'app_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_role')
\gexec

ALTER ROLE :"migration_role" NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE :"app_role" NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

SELECT format(
    'CREATE DATABASE %I WITH OWNER %I ENCODING %L LC_COLLATE %L LC_CTYPE %L TEMPLATE template0',
    :'db_name',
    :'migration_role',
    'UTF8',
    'C',
    'C'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')
\gexec

ALTER DATABASE :"db_name" SET timezone TO 'UTC';

\connect :db_name

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;

GRANT CONNECT ON DATABASE :"db_name" TO :"migration_role";
GRANT CONNECT ON DATABASE :"db_name" TO :"app_role";

GRANT USAGE, CREATE ON SCHEMA public TO :"migration_role";
GRANT USAGE ON SCHEMA public TO :"app_role";

-- Run this after Flyway has created the schema, or re-run it safely after each migration window.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"app_role";

ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_role" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_role";

ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_role" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"app_role";
