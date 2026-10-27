-- Production catalog-audit role setup.
-- Run as a database administrator. This creates a NOLOGIN role only; it does
-- not create credentials, grant application-table access, or modify app data.
-- Review the schema list before running if the fingerprint scope changes.

DO $role$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'catalog_audit_ro') THEN
    CREATE ROLE catalog_audit_ro
      NOLOGIN
      NOSUPERUSER
      NOCREATEDB
      NOCREATEROLE
      NOINHERIT
      NOREPLICATION
      NOBYPASSRLS;
  END IF;
END
$role$;

ALTER ROLE catalog_audit_ro SET default_transaction_read_only = on;
ALTER ROLE catalog_audit_ro SET statement_timeout = '30s';

GRANT USAGE ON SCHEMA public, private TO catalog_audit_ro;

-- Intentionally do not grant SELECT on application tables.
-- The fingerprint queries PostgreSQL catalogs and metadata views only.
