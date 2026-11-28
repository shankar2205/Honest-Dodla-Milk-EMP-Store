-- Read-only production catalog fingerprint.
-- Run only with a SELECT-capable connection against the intended database:
--   psql "$READ_ONLY_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/production_catalog_fingerprint.sql
-- This script performs catalog SELECTs only. It does not modify schema, data, grants, or migration history.
-- Review the output before using it for any migration decision.

\echo '=== Migration ledger ==='
select version, name
from supabase_migrations.schema_migrations
order by version;

\echo '=== Installed extensions ==='
select e.extname, e.extversion, n.nspname as schema_name
from pg_extension e
join pg_namespace n on n.oid = e.extnamespace
order by e.extname;

\echo '=== Public enum types and labels ==='
select n.nspname as schema_name, t.typname as type_name,
       e.enumsortorder, e.enumlabel
from pg_type t
join pg_namespace n on n.oid = t.typnamespace
join pg_enum e on e.enumtypid = t.oid
where n.nspname in ('public', 'private')
order by n.nspname, t.typname, e.enumsortorder;

\echo '=== Public tables, columns, and row estimates ==='
select
  c.relname as table_name,
  a.attnum as ordinal,
  a.attname as column_name,
  pg_catalog.format_type(a.atttypid, a.atttypmod) as data_type,
  a.attnotnull as not_null,
  pg_get_expr(d.adbin, d.adrelid) as default_or_generated,
  c.relrowsecurity as rls_enabled
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
join pg_attribute a on a.attrelid = c.oid
left join pg_attrdef d on d.adrelid = c.oid and d.adnum = a.attnum
where n.nspname = 'public'
  and c.relkind in ('r', 'p')
  and a.attnum > 0
  and not a.attisdropped
order by c.relname, a.attnum;

\echo '=== Constraints ==='
select
  conrelid::regclass::text as table_name,
  conname,
  contype,
  convalidated,
  pg_get_constraintdef(oid, true) as definition
from pg_constraint
where connamespace = 'public'::regnamespace
order by conrelid::regclass::text, conname;

\echo '=== RLS policies ==='
select schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
from pg_policies
where schemaname in ('public', 'private')
order by schemaname, tablename, policyname;

\echo '=== Relevant function signatures, security, search_path, and grants ==='
select
  n.nspname as schema_name,
  p.proname as function_name,
  pg_get_function_identity_arguments(p.oid) as identity_arguments,
  p.prosecdef as security_definer,
  p.proconfig as function_settings,
  has_function_privilege('anon', p.oid, 'EXECUTE') as anon_can_execute,
  has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_can_execute,
  pg_get_functiondef(p.oid) as definition
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname in ('public', 'private')
  and p.proname in (
    'create_transaction_batch',
    'create_employee',
    'create_guest_employee',
    'create_guest_employee_with_code',
    'find_guest_by_phone',
    'next_guest_code',
    'current_user_role',
    'operator_daily_sales',
    'set_transaction_price',
    'set_updated_at'
  )
order by n.nspname, p.proname, pg_get_function_identity_arguments(p.oid);

\echo '=== Views ==='
select schemaname, viewname, definition
from pg_views
where schemaname = 'public'
order by viewname;

\echo '=== Triggers ==='
select
  event_object_schema as schema_name,
  event_object_table as table_name,
  trigger_name,
  action_timing,
  event_manipulation,
  action_orientation,
  action_statement
from information_schema.triggers
where event_object_schema in ('public', 'private')
order by event_object_schema, event_object_table, trigger_name, event_manipulation;

\echo '=== Indexes ==='
select schemaname, tablename, indexname, indexdef
from pg_indexes
where schemaname = 'public'
order by tablename, indexname;

\echo '=== Sequences and role privileges ==='
select
  schemaname, sequencename, data_type, start_value, min_value,
  max_value, increment_by, cycle, cache_size, last_value,
  has_sequence_privilege('anon', format('%I.%I', schemaname, sequencename), 'USAGE') as anon_usage,
  has_sequence_privilege('anon', format('%I.%I', schemaname, sequencename), 'SELECT') as anon_select,
  has_sequence_privilege('authenticated', format('%I.%I', schemaname, sequencename), 'USAGE') as authenticated_usage,
  has_sequence_privilege('authenticated', format('%I.%I', schemaname, sequencename), 'SELECT') as authenticated_select
from pg_sequences
where schemaname in ('public', 'private')
order by schemaname, sequencename;

\echo '=== Public table and view grants ==='
select
  table_schema, table_name, grantee, privilege_type, is_grantable
from information_schema.role_table_grants
where table_schema = 'public'
  and grantee in ('anon', 'authenticated', 'service_role', 'postgres')
order by table_schema, table_name, grantee, privilege_type;

