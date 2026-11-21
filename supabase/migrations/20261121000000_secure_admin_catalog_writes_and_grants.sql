-- Centralize the remaining admin catalog writes in SECURITY DEFINER RPCs.
-- Preserve read access for authenticated app users while removing direct table writes.
-- Existing legacy employee codes and all application rows remain untouched.

create or replace function public.update_employee_details(
  p_employee_id uuid,
  p_name text,
  p_department text default null,
  p_phone text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if coalesce(private.current_user_role()::text, '') <> 'ADMIN' then
    raise exception 'Administrator access required' using errcode = '42501';
  end if;
  if p_employee_id is null or nullif(trim(coalesce(p_name, '')), '') is null then
    raise exception 'Employee and non-empty name are required' using errcode = '22023';
  end if;

  update public.employees
  set name = trim(p_name),
      department = nullif(trim(coalesce(p_department, '')), ''),
      phone = nullif(trim(coalesce(p_phone, '')), '')
  where id = p_employee_id;

  if not found then
    raise exception 'Employee was not found' using errcode = 'P0002';
  end if;
end;
$function$;

create or replace function public.set_employee_active(
  p_employee_id uuid,
  p_active boolean
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if coalesce(private.current_user_role()::text, '') <> 'ADMIN' then
    raise exception 'Administrator access required' using errcode = '42501';
  end if;
  if p_employee_id is null or p_active is null then
    raise exception 'Employee and active status are required' using errcode = '22023';
  end if;

  update public.employees
  set active = p_active
  where id = p_employee_id;

  if not found then
    raise exception 'Employee was not found' using errcode = 'P0002';
  end if;
end;
$function$;

create or replace function public.set_product_variant_active(
  p_variant_id uuid,
  p_active boolean
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if coalesce(private.current_user_role()::text, '') <> 'ADMIN' then
    raise exception 'Administrator access required' using errcode = '42501';
  end if;
  if p_variant_id is null or p_active is null then
    raise exception 'Variant and active status are required' using errcode = '22023';
  end if;

  update public.product_variants
  set active = p_active
  where id = p_variant_id;

  if not found then
    raise exception 'Product variant was not found' using errcode = 'P0002';
  end if;
end;
$function$;

revoke all on function public.update_employee_details(uuid, text, text, text) from public, anon;
revoke all on function public.set_employee_active(uuid, boolean) from public, anon;
revoke all on function public.set_product_variant_active(uuid, boolean) from public, anon;
grant execute on function public.update_employee_details(uuid, text, text, text) to authenticated;
grant execute on function public.set_employee_active(uuid, boolean) to authenticated;
grant execute on function public.set_product_variant_active(uuid, boolean) to authenticated;

-- Existing application tables remain readable by authenticated users.
-- Only the transaction RPC requires direct INSERT because it is SECURITY INVOKER.
-- The transaction report view is SECURITY INVOKER and therefore also needs
-- authenticated SELECT on its underlying tables.
revoke all on table
  public.employees,
  public.products,
  public.product_variants,
  public.profiles,
  public.transactions,
  public.transaction_report
from anon, authenticated;

grant select on table
  public.employees,
  public.products,
  public.product_variants,
  public.profiles,
  public.transactions,
  public.transaction_report
to authenticated;

grant insert on table public.transactions to authenticated;

-- Guest IDs are allocated inside SECURITY DEFINER RPCs; callers do not need
-- direct sequence privileges.
revoke all on all sequences in schema public from anon, authenticated;

-- Prevent future objects created by the application owner from inheriting
-- broad client privileges. Future API objects must grant access explicitly.
alter default privileges for role postgres in schema public
  revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public
  revoke all on sequences from anon, authenticated;
-- PostgreSQL's default PUBLIC EXECUTE privilege is global, not schema-scoped.
-- Do not change it here: doing so would also affect postgres-owned functions in
-- Supabase-managed schemas such as storage. Instead, every SECURITY DEFINER
-- function created by application migrations must explicitly REVOKE EXECUTE
-- from PUBLIC/anon and grant only its intended caller roles.
alter default privileges for role postgres in schema public
  revoke execute on functions from anon, authenticated;
