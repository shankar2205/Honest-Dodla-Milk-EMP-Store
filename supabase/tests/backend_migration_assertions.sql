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
  if not coalesce(v_fn.proconfig @> array['search_path=""'], false) then
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
  if not coalesce(v_fn.proconfig @> array['search_path=""'], false) then
    raise exception 'save_product_variant must set an empty search_path';
  end if;
  if not has_function_privilege('authenticated', 'public.save_product_variant(uuid,text,text,integer,numeric)', 'EXECUTE') then
    raise exception 'authenticated must be able to execute save_product_variant';
  end if;
  if has_function_privilege('anon', 'public.save_product_variant(uuid,text,text,integer,numeric)', 'EXECUTE') then
    raise exception 'anon must not be able to execute save_product_variant';
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
    if not coalesce(v_fn.proconfig @> array['search_path=""'], false) then
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
    if not coalesce(v_fn.proconfig @> array['search_path=""'], false) then
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
