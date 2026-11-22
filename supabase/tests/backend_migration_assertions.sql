-- Read-only assertions for the backend hardening migrations.
-- Run against a disposable local/test Supabase database AFTER migrations are applied:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/backend_migration_assertions.sql
-- This file deliberately does not insert/update/delete application data.

do $$
declare
  v_fn record;
  v_policy text;
begin
  select p.prosecdef, p.proconfig
    into v_fn
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname = 'create_transaction_batch'
    and pg_get_function_identity_arguments(p.oid) = 'p_employee_id uuid, p_items jsonb';

  if not found then
    raise exception 'Missing create_transaction_batch(uuid,jsonb)';
  end if;
  if v_fn.prosecdef then
    raise exception 'create_transaction_batch must be SECURITY INVOKER';
  end if;
  if not coalesce(v_fn.proconfig @> array['search_path=' || chr(34) || chr(34)], false) then
    raise exception 'create_transaction_batch must set an empty search_path';
  end if;

  if not has_function_privilege('authenticated', 'public.create_transaction_batch(uuid,jsonb)', 'EXECUTE') then
    raise exception 'authenticated must be able to execute create_transaction_batch';
  end if;
  if has_function_privilege('anon', 'public.create_transaction_batch(uuid,jsonb)', 'EXECUTE') then
    raise exception 'anon must not be able to execute create_transaction_batch';
  end if;

  select p.prosecdef, p.proconfig
    into v_fn
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname = 'save_product_variant'
    and pg_get_function_identity_arguments(p.oid) =
      'p_variant_id uuid, p_product_name text, p_variant_name text, p_unit_volume_ml integer, p_price numeric';

  if not found then
    raise exception 'Missing save_product_variant(uuid,text,text,integer,numeric)';
  end if;
  if not v_fn.prosecdef then
    raise exception 'save_product_variant must be SECURITY DEFINER to perform privileged catalog writes after its explicit ADMIN role check';
  end if;
  if not coalesce(v_fn.proconfig @> array['search_path=' || chr(34) || chr(34)], false) then
    raise exception 'save_product_variant must set an empty search_path';
  end if;
  if not has_function_privilege('authenticated', 'public.save_product_variant(uuid,text,text,integer,numeric)', 'EXECUTE') then
    raise exception 'authenticated must be able to execute save_product_variant';
  end if;
  if has_function_privilege('anon', 'public.save_product_variant(uuid,text,text,integer,numeric)', 'EXECUTE') then
    raise exception 'anon must not be able to execute save_product_variant';
  end if;

  select p.prosecdef, p.proconfig
    into v_fn
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname = 'update_profile_access'
    and pg_get_function_identity_arguments(p.oid) =
      'p_user_id uuid, p_role user_role, p_active boolean';

  if not found then
    raise exception 'Missing update_profile_access(uuid,user_role,boolean)';
  end if;
  if not v_fn.prosecdef then
    raise exception 'update_profile_access must be SECURITY DEFINER';
  end if;
  if not coalesce(v_fn.proconfig @> array['search_path=' || chr(34) || chr(34)], false) then
    raise exception 'update_profile_access must set an empty search_path';
  end if;
  if not has_function_privilege('authenticated', 'public.update_profile_access(uuid,public.user_role,boolean)', 'EXECUTE') then
    raise exception 'authenticated must be able to execute update_profile_access';
  end if;
  if has_function_privilege('anon', 'public.update_profile_access(uuid,public.user_role,boolean)', 'EXECUTE') then
    raise exception 'anon must not be able to execute update_profile_access';
  end if;
  if has_table_privilege('authenticated', 'public.profiles', 'UPDATE') then
    raise exception 'authenticated must not directly update profiles';
  end if;

  foreach v_policy in array array['find_guest_by_phone', 'create_guest_employee_with_code']
  loop
    select p.oid, p.prosecdef, p.proconfig
      into v_fn
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = v_policy
    order by p.oid
    limit 1;

    if not found then
      raise exception 'Missing expected guest RPC: %', v_policy;
    end if;
    if not v_fn.prosecdef then
      raise exception '% must remain SECURITY DEFINER', v_policy;
    end if;
    if not coalesce(v_fn.proconfig @> array['search_path=' || chr(34) || chr(34)], false) then
      raise exception '% must set an empty search_path', v_policy;
    end if;
    if not has_function_privilege('authenticated', v_fn.oid, 'EXECUTE') then
      raise exception 'authenticated must retain EXECUTE on %', v_policy;
    end if;
    if has_function_privilege('anon', v_fn.oid, 'EXECUTE') then
      raise exception 'anon must not have EXECUTE on %', v_policy;
    end if;
  end loop;

  foreach v_policy in array array['create_employee', 'create_guest_employee']
  loop
    select p.oid, p.prosecdef, p.proconfig
      into v_fn
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = v_policy
    order by p.oid
    limit 1;

    if not found then
      raise exception 'Missing expected RPC: %', v_policy;
    end if;
    if not v_fn.prosecdef then
      raise exception '% must remain SECURITY DEFINER', v_policy;
    end if;
    if not coalesce(v_fn.proconfig @> array['search_path=' || chr(34) || chr(34)], false) then
      raise exception '% must set an empty search_path', v_policy;
    end if;
    if not has_function_privilege('authenticated', v_fn.oid, 'EXECUTE') then
      raise exception 'authenticated must retain EXECUTE on %', v_policy;
    end if;
    if has_function_privilege('anon', v_fn.oid, 'EXECUTE') then
      raise exception 'anon must not have EXECUTE on %', v_policy;
    end if;
  end loop;

  if has_function_privilege('anon', 'public.next_guest_code()', 'EXECUTE') then
    raise exception 'anon must not be able to execute next_guest_code';
  end if;
  if has_function_privilege('authenticated', 'public.next_guest_code()', 'EXECUTE') then
    raise exception 'authenticated must not directly execute next_guest_code';
  end if;
  if has_sequence_privilege('authenticated', 'public.guest_code_seq', 'USAGE') then
    raise exception 'authenticated must not directly use guest_code_seq';
  end if;
  if has_sequence_privilege('anon', 'public.guest_code_seq', 'USAGE') then
    raise exception 'anon must not have USAGE on guest_code_seq';
  end if;

  select qual into v_policy
  from pg_policies
  where schemaname = 'public'
    and tablename = 'profiles'
    and policyname = 'users can read their own profile'
    and cmd = 'SELECT'
    and roles = array['authenticated']::name[];

  if v_policy is null or position('( SELECT AUTH.UID()' in upper(v_policy)) = 0
     or position('active' in v_policy) = 0 then
    raise exception 'Profile self-read policy missing expected auth.uid() initplan and active predicate; found: %', v_policy;
  end if;

  select with_check into v_policy
  from pg_policies
  where schemaname = 'public'
    and tablename = 'transactions'
    and policyname = 'operators can create transactions'
    and cmd = 'INSERT'
    and roles = array['authenticated']::name[];

  if v_policy is null
     or position('( SELECT AUTH.UID()' in upper(v_policy)) = 0
     or position('current_user_role' in v_policy) = 0 then
    raise exception 'Transaction insert policy missing expected auth.uid() initplan or role predicate; found: %', v_policy;
  end if;
end;
$$;

select 'Backend migration catalog assertions passed' as result;

-- Employee-code compatibility gate:
-- Preserve legacy rows by enforcing the six-digit format in the creation RPC only.
-- Do not add a table CHECK: even NOT VALID checks are enforced on updated legacy rows.
do $employee_code_gate$
declare
  v_fn_definition text;
begin
  if exists (
    select 1 from pg_constraint
    where conrelid = 'public.employees'::regclass
      and conname = 'employees_employee_code_six_digits_check'
  ) then
    raise exception 'Six-digit employee-code table CHECK must remain absent for this rollout';
  end if;

  select pg_get_functiondef(p.oid)
    into v_fn_definition
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname = 'create_employee'
    and pg_get_function_identity_arguments(p.oid) =
      'p_employee_code text, p_name text, p_phone text, p_department text';

  if v_fn_definition is null
     or strpos(v_fn_definition, '^[0-9]{6}' || chr(36)) = 0 then
    raise exception 'create_employee must independently enforce exactly six digits';
  end if;
end;
$employee_code_gate$;

-- Remaining admin catalog writes must use guarded RPCs, not direct table UPDATEs.
do $admin_catalog_write_gate$
declare
  v_name text;
  v_args text;
  v_fn record;
  v_table text;
  v_privilege text;
begin
  for v_name, v_args in
    select * from (values
      ('update_employee_details', 'p_employee_id uuid, p_name text, p_department text, p_phone text'),
      ('set_employee_active', 'p_employee_id uuid, p_active boolean'),
      ('set_product_variant_active', 'p_variant_id uuid, p_active boolean')
    ) as expected(name, args)
  loop
    select p.oid, p.prosecdef, p.proconfig
      into v_fn
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = v_name
      and pg_get_function_identity_arguments(p.oid) = v_args;

    if not found then
      raise exception 'Missing admin catalog RPC: %', v_name;
    end if;
    if not v_fn.prosecdef
       or not coalesce(v_fn.proconfig @> array['search_path=' || chr(34) || chr(34)], false) then
      raise exception '% must be SECURITY DEFINER with empty search_path', v_name;
    end if;
    if not has_function_privilege('authenticated', v_fn.oid, 'EXECUTE')
       or has_function_privilege('anon', v_fn.oid, 'EXECUTE') then
      raise exception 'Unexpected EXECUTE grants on %', v_name;
    end if;
  end loop;

  foreach v_table in array array[
    'public.employees', 'public.products', 'public.product_variants',
    'public.profiles', 'public.transactions', 'public.transaction_report'
  ]
  loop
    if not has_table_privilege('authenticated', v_table, 'SELECT') then
      raise exception 'authenticated SELECT missing on %', v_table;
    end if;
    if has_table_privilege('anon', v_table, 'SELECT') then
      raise exception 'anon must not SELECT %', v_table;
    end if;

    foreach v_privilege in array array['INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER']
    loop
      if has_table_privilege('anon', v_table, v_privilege) then
        raise exception 'anon must not have % on %', v_privilege, v_table;
      end if;
      if has_table_privilege('authenticated', v_table, v_privilege)
         and not (v_table = 'public.transactions' and v_privilege = 'INSERT') then
        raise exception 'authenticated must not have direct % on %', v_privilege, v_table;
      end if;
    end loop;
  end loop;

  if not has_table_privilege('authenticated', 'public.transactions', 'INSERT') then
    raise exception 'authenticated INSERT is required by SECURITY INVOKER create_transaction_batch';
  end if;

  -- Trigger implementation functions are not API entry points. Revoke direct
  -- client execution while retaining their use by already-created triggers.
  if has_function_privilege('anon', 'public.set_updated_at()', 'EXECUTE')
     or has_function_privilege('authenticated', 'public.set_updated_at()', 'EXECUTE')
     or has_function_privilege('anon', 'public.set_transaction_price()', 'EXECUTE')
     or has_function_privilege('authenticated', 'public.set_transaction_price()', 'EXECUTE') then
    raise exception 'Client roles must not directly execute trigger-only functions';
  end if;

  if exists (
    select 1
    from pg_proc p
    cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
    where p.oid in ('public.set_updated_at()'::regprocedure,
                    'public.set_transaction_price()'::regprocedure)
      and a.grantee = 0
      and a.privilege_type = 'EXECUTE'
  ) then
    raise exception 'PUBLIC must not directly execute trigger-only functions';
  end if;

  -- Future postgres-owned public objects must not silently regain client grants.
  if exists (
    select 1
    from pg_default_acl d
    join pg_namespace n on n.oid = d.defaclnamespace
    cross join lateral aclexplode(d.defaclacl) a
    where d.defaclrole = 'postgres'::regrole
      and n.nspname = 'public'
      and d.defaclobjtype in ('r','S','f')
      and a.grantee in ('anon'::regrole, 'authenticated'::regrole)
  ) then
    raise exception 'postgres default privileges still grant anon/authenticated access to future public objects';
  end if;

  -- PUBLIC EXECUTE remains PostgreSQL's global default; application migrations
  -- must explicitly revoke it on every privileged RPC they create. Avoid a
  -- global default-privilege change that would alter Supabase-managed schemas.
end;
$admin_catalog_write_gate$;
 in v_fn_definition) = 0 then
    raise exception 'create_employee must independently enforce exactly six digits';
  end if;
end;
$employee_code_gate$;

-- Remaining admin catalog writes must use guarded RPCs, not direct table UPDATEs.
do $admin_catalog_write_gate$
declare
  v_name text;
  v_args text;
  v_fn record;
  v_table text;
  v_privilege text;
begin
  for v_name, v_args in
    select * from (values
      ('update_employee_details', 'p_employee_id uuid, p_name text, p_department text, p_phone text'),
      ('set_employee_active', 'p_employee_id uuid, p_active boolean'),
      ('set_product_variant_active', 'p_variant_id uuid, p_active boolean')
    ) as expected(name, args)
  loop
    select p.oid, p.prosecdef, p.proconfig
      into v_fn
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = v_name
      and pg_get_function_identity_arguments(p.oid) = v_args;

    if not found then
      raise exception 'Missing admin catalog RPC: %', v_name;
    end if;
    if not v_fn.prosecdef
       or not coalesce(v_fn.proconfig @> array['search_path=' || chr(34) || chr(34)], false) then
      raise exception '% must be SECURITY DEFINER with empty search_path', v_name;
    end if;
    if not has_function_privilege('authenticated', v_fn.oid, 'EXECUTE')
       or has_function_privilege('anon', v_fn.oid, 'EXECUTE') then
      raise exception 'Unexpected EXECUTE grants on %', v_name;
    end if;
  end loop;

  foreach v_table in array array[
    'public.employees', 'public.products', 'public.product_variants',
    'public.profiles', 'public.transactions', 'public.transaction_report'
  ]
  loop
    if not has_table_privilege('authenticated', v_table, 'SELECT') then
      raise exception 'authenticated SELECT missing on %', v_table;
    end if;
    if has_table_privilege('anon', v_table, 'SELECT') then
      raise exception 'anon must not SELECT %', v_table;
    end if;

    foreach v_privilege in array array['INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER']
    loop
      if has_table_privilege('anon', v_table, v_privilege) then
        raise exception 'anon must not have % on %', v_privilege, v_table;
      end if;
      if has_table_privilege('authenticated', v_table, v_privilege)
         and not (v_table = 'public.transactions' and v_privilege = 'INSERT') then
        raise exception 'authenticated must not have direct % on %', v_privilege, v_table;
      end if;
    end loop;
  end loop;

  if not has_table_privilege('authenticated', 'public.transactions', 'INSERT') then
    raise exception 'authenticated INSERT is required by SECURITY INVOKER create_transaction_batch';
  end if;

  -- Trigger implementation functions are not API entry points. Revoke direct
  -- client execution while retaining their use by already-created triggers.
  if has_function_privilege('anon', 'public.set_updated_at()', 'EXECUTE')
     or has_function_privilege('authenticated', 'public.set_updated_at()', 'EXECUTE')
     or has_function_privilege('anon', 'public.set_transaction_price()', 'EXECUTE')
     or has_function_privilege('authenticated', 'public.set_transaction_price()', 'EXECUTE') then
    raise exception 'Client roles must not directly execute trigger-only functions';
  end if;

  if exists (
    select 1
    from pg_proc p
    cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
    where p.oid in ('public.set_updated_at()'::regprocedure,
                    'public.set_transaction_price()'::regprocedure)
      and a.grantee = 0
      and a.privilege_type = 'EXECUTE'
  ) then
    raise exception 'PUBLIC must not directly execute trigger-only functions';
  end if;

  -- Future postgres-owned public objects must not silently regain client grants.
  if exists (
    select 1
    from pg_default_acl d
    join pg_namespace n on n.oid = d.defaclnamespace
    cross join lateral aclexplode(d.defaclacl) a
    where d.defaclrole = 'postgres'::regrole
      and n.nspname = 'public'
      and d.defaclobjtype in ('r','S','f')
      and a.grantee in ('anon'::regrole, 'authenticated'::regrole)
  ) then
    raise exception 'postgres default privileges still grant anon/authenticated access to future public objects';
  end if;

  -- PUBLIC EXECUTE remains PostgreSQL's global default; application migrations
  -- must explicitly revoke it on every privileged RPC they create. Avoid a
  -- global default-privilege change that would alter Supabase-managed schemas.
end;
$admin_catalog_write_gate$;
