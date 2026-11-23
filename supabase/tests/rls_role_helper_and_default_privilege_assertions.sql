-- Catalog/security assertions for the RLS helper and default-privilege reconciliation.
-- Run only against a disposable database after the candidate migrations.

do $assertions$
declare
  v_count integer;
  v_fn record;
  v_policy record;
begin
  select p.prosecdef, p.proconfig
    into v_fn
  from pg_proc p
  where p.oid = 'private.current_user_role()'::regprocedure;

  if not found or not v_fn.prosecdef then
    raise exception 'private.current_user_role() must exist and remain SECURITY DEFINER';
  end if;
  if not coalesce(v_fn.proconfig @> array['search_path=' || chr(34) || chr(34)], false) then
    raise exception 'private.current_user_role() must use an empty search_path';
  end if;
  if has_function_privilege('anon', 'private.current_user_role()', 'EXECUTE')
     or has_function_privilege('service_role', 'private.current_user_role()', 'EXECUTE')
     or not has_function_privilege('authenticated', 'private.current_user_role()', 'EXECUTE') then
    raise exception 'private.current_user_role() grants must be authenticated-only';
  end if;

  if has_function_privilege('anon', 'public.current_user_role()', 'EXECUTE')
     or has_function_privilege('service_role', 'public.current_user_role()', 'EXECUTE')
     or not has_function_privilege('authenticated', 'public.current_user_role()', 'EXECUTE') then
    raise exception 'public.current_user_role() must be restricted to authenticated compatibility calls';
  end if;

  -- These six policies must each depend on the private helper, not the
  -- legacy public helper. pg_depend captures policy expression dependencies.
  for v_policy in
    select * from (values
      ('employees', 'admins manage employees'),
      ('products', 'admins manage products'),
      ('product_variants', 'admins manage variants'),
      ('profiles', 'admins manage profiles'),
      ('transactions', 'operators can create transactions'),
      ('transactions', 'admins and operators can read transactions')
    ) expected(table_name, policy_name)
  loop
    if not exists (
      select 1
      from pg_policy pol
      join pg_class c on c.oid = pol.polrelid
      join pg_namespace n on n.oid = c.relnamespace
      join pg_depend d
        on d.classid = 'pg_policy'::regclass
       and d.objid = pol.oid
       and d.refclassid = 'pg_proc'::regclass
       and d.refobjid = 'private.current_user_role()'::regprocedure
      where n.nspname = 'public'
        and c.relname = v_policy.table_name
        and pol.polname = v_policy.policy_name
    ) then
      raise exception 'Policy %.% does not depend on private.current_user_role()',
        v_policy.table_name, v_policy.policy_name;
    end if;
  end loop;

  select count(distinct pol.oid) into v_count
  from pg_policy pol
  join pg_depend d
    on d.classid = 'pg_policy'::regclass
   and d.objid = pol.oid
   and d.refclassid = 'pg_proc'::regclass
   and d.refobjid = 'private.current_user_role()'::regprocedure
  join pg_class c on c.oid = pol.polrelid
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and (c.relname, pol.polname) in (
      ('employees', 'admins manage employees'),
      ('products', 'admins manage products'),
      ('product_variants', 'admins manage variants'),
      ('profiles', 'admins manage profiles'),
      ('transactions', 'operators can create transactions'),
      ('transactions', 'admins and operators can read transactions')
    );
  if v_count <> 6 then
    raise exception 'Expected six RLS policies bound to private.current_user_role(), found %', v_count;
  end if;

  if exists (
    select 1
    from pg_policy pol
    join pg_depend d
      on d.classid = 'pg_policy'::regclass
     and d.objid = pol.oid
     and d.refclassid = 'pg_proc'::regclass
     and d.refobjid = 'public.current_user_role()'::regprocedure
  ) then
    raise exception 'An RLS policy still depends on public.current_user_role()';
  end if;

  -- Both object-creation owners must stop granting table/sequence/function
  -- access to anon/authenticated by default. service_role is intentionally kept.
  if exists (
    select 1
    from pg_default_acl d
    join pg_namespace n on n.oid = d.defaclnamespace
    cross join lateral aclexplode(d.defaclacl) a
    where n.nspname = 'public'
      and d.defaclrole in ('postgres'::regrole, 'supabase_admin'::regrole)
      and d.defaclobjtype in ('r', 'S', 'f')
      and a.grantee in ('anon'::regrole, 'authenticated'::regrole)
  ) then
    raise exception 'postgres/supabase_admin default privileges still grant anon/authenticated access';
  end if;
end;
$assertions$;

select 'RLS helper dependencies and default privilege assertions passed' as result;
