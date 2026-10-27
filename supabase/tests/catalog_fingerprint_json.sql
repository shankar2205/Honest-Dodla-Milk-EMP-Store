-- Machine-readable, read-only catalog snapshot for comparing databases.
-- Generate with:
--   psql -X -A -t "$DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/catalog_fingerprint_json.sql > catalog.json
-- Capture each database with this same file/version. This contains metadata only;
-- it intentionally excludes application rows, sequence last_value, and migration history.
select jsonb_pretty(jsonb_build_object(
  'extensions', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.extname)
    from (
      select e.extname, e.extversion, n.nspname as schema_name
      from pg_extension e join pg_namespace n on n.oid = e.extnamespace
    ) x
  ), '[]'::jsonb),

  'enums', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.schema_name, x.type_name, x.sort_order)
    from (
      select n.nspname as schema_name, t.typname as type_name,
             e.enumsortorder as sort_order, e.enumlabel
      from pg_type t
      join pg_namespace n on n.oid = t.typnamespace
      join pg_enum e on e.enumtypid = t.oid
      where n.nspname in ('public', 'private')
    ) x
  ), '[]'::jsonb),

  'columns', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.object_key)
    from (
      select n.nspname || '.' || c.relname || '.' || a.attname as object_key,
             n.nspname as schema_name, c.relname as table_name,
             c.relkind as relation_kind, a.attnum as ordinal,
             a.attname as column_name,
             pg_catalog.format_type(a.atttypid, a.atttypmod) as data_type,
             a.attnotnull as not_null,
             a.attidentity as identity_kind,
             a.attgenerated as generated_kind,
             pg_get_expr(d.adbin, d.adrelid) as default_or_generated,
             c.relrowsecurity as rls_enabled,
             c.relforcerowsecurity as force_rls
      from pg_class c
      join pg_namespace n on n.oid = c.relnamespace
      join pg_attribute a on a.attrelid = c.oid
      left join pg_attrdef d on d.adrelid = c.oid and d.adnum = a.attnum
      where n.nspname in ('public', 'private')
        and c.relkind in ('r', 'p', 'v', 'm', 'f')
        and a.attnum > 0 and not a.attisdropped
    ) x
  ), '[]'::jsonb),

  'constraints', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.object_key)
    from (
      select conrelid::regclass::text || '.' || conname as object_key,
             conrelid::regclass::text as table_name, conname,
             contype, convalidated, pg_get_constraintdef(oid, true) as definition
      from pg_constraint
      where connamespace in ('public'::regnamespace, 'private'::regnamespace)
    ) x
  ), '[]'::jsonb),

  'policies', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.object_key)
    from (
      select schemaname || '.' || tablename || '.' || policyname as object_key,
             schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
      from pg_policies
      where schemaname in ('public', 'private')
    ) x
  ), '[]'::jsonb),

  'functions', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.object_key)
    from (
      select n.nspname || '.' || p.proname || '(' ||
             pg_get_function_identity_arguments(p.oid) || ')' as object_key,
             n.nspname as schema_name, p.proname as function_name,
             pg_get_function_identity_arguments(p.oid) as identity_arguments,
             pg_get_function_result(p.oid) as result_type,
             l.lanname as language, p.prosecdef as security_definer,
             p.proleakproof as leakproof, p.provolatile as volatility,
             p.proconfig as function_settings,
             has_function_privilege('anon', p.oid, 'EXECUTE') as anon_can_execute,
             has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_can_execute,
             has_function_privilege('service_role', p.oid, 'EXECUTE') as service_role_can_execute,
             pg_get_functiondef(p.oid) as definition
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
      join pg_language l on l.oid = p.prolang
      where n.nspname in ('public', 'private')
        and p.prokind in ('f', 'p', 'w')
    ) x
  ), '[]'::jsonb),

  'views', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.object_key)
    from (
      select schemaname || '.' || viewname as object_key,
             schemaname, viewname, definition
      from pg_views where schemaname in ('public', 'private')
    ) x
  ), '[]'::jsonb),

  'triggers', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.object_key)
    from (
      select event_object_schema || '.' || event_object_table || '.' || trigger_name ||
             '.' || event_manipulation as object_key,
             event_object_schema as schema_name, event_object_table as table_name,
             trigger_name, action_timing, event_manipulation,
             action_orientation, action_statement
      from information_schema.triggers
      where event_object_schema in ('public', 'private')
    ) x
  ), '[]'::jsonb),

  'indexes', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.object_key)
    from (
      select schemaname || '.' || tablename || '.' || indexname as object_key,
             schemaname, tablename, indexname, indexdef
      from pg_indexes where schemaname in ('public', 'private')
    ) x
  ), '[]'::jsonb),

  'sequences', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.object_key)
    from (
      select schemaname || '.' || sequencename as object_key,
             schemaname, sequencename, data_type, start_value, min_value,
             max_value, increment_by, cycle, cache_size,
             has_sequence_privilege('anon', format('%I.%I', schemaname, sequencename), 'USAGE') as anon_usage,
             has_sequence_privilege('anon', format('%I.%I', schemaname, sequencename), 'SELECT') as anon_select,
             has_sequence_privilege('authenticated', format('%I.%I', schemaname, sequencename), 'USAGE') as authenticated_usage,
             has_sequence_privilege('authenticated', format('%I.%I', schemaname, sequencename), 'SELECT') as authenticated_select,
             has_sequence_privilege('service_role', format('%I.%I', schemaname, sequencename), 'USAGE') as service_role_usage,
             has_sequence_privilege('service_role', format('%I.%I', schemaname, sequencename), 'SELECT') as service_role_select
      from pg_sequences where schemaname in ('public', 'private')
    ) x
  ), '[]'::jsonb),

  'table_grants', coalesce((
    select jsonb_agg(to_jsonb(x) order by x.object_key)
    from (
      select table_schema || '.' || table_name || '.' || grantee || '.' || privilege_type as object_key,
             table_schema, table_name, grantee, privilege_type, is_grantable
      from information_schema.role_table_grants
      where table_schema in ('public', 'private')
        and grantee in ('anon', 'authenticated', 'service_role', 'postgres')
    ) x
  ), '[]'::jsonb)
))::text;
