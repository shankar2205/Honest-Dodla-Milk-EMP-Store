-- Reconcile the application RLS role helper with the hardened private helper.
-- Keep the public helper available to authenticated legacy clients, but remove
-- PUBLIC/anon access. All policies use private.current_user_role() consistently.
-- Tighten future public-object defaults for both known object-creation owners.

drop policy if exists "admins manage employees" on public.employees;
create policy "admins manage employees" on public.employees
  for all to authenticated
  using (private.current_user_role() = 'ADMIN'::public.user_role)
  with check (private.current_user_role() = 'ADMIN'::public.user_role);

drop policy if exists "admins manage products" on public.products;
create policy "admins manage products" on public.products
  for all to authenticated
  using (private.current_user_role() = 'ADMIN'::public.user_role)
  with check (private.current_user_role() = 'ADMIN'::public.user_role);

drop policy if exists "admins manage variants" on public.product_variants;
create policy "admins manage variants" on public.product_variants
  for all to authenticated
  using (private.current_user_role() = 'ADMIN'::public.user_role)
  with check (private.current_user_role() = 'ADMIN'::public.user_role);

drop policy if exists "admins manage profiles" on public.profiles;
create policy "admins manage profiles" on public.profiles
  for all to authenticated
  using (private.current_user_role() = 'ADMIN'::public.user_role)
  with check (private.current_user_role() = 'ADMIN'::public.user_role);

drop policy if exists "operators can create transactions" on public.transactions;
create policy "operators can create transactions" on public.transactions
  for insert to authenticated
  with check (
    operator_id = (select auth.uid())
    and private.current_user_role() = any (
      array['ADMIN'::public.user_role, 'OPERATOR'::public.user_role]
    )
  );

drop policy if exists "admins and operators can read transactions" on public.transactions;
create policy "admins and operators can read transactions" on public.transactions
  for select to authenticated
  using (
    private.current_user_role() = any (
      array['ADMIN'::public.user_role, 'OPERATOR'::public.user_role]
    )
  );

-- Remove the anonymous/PUBLIC RPC exposure from the legacy helper. Keep
-- authenticated EXECUTE temporarily for compatibility with any older client.
revoke all on function public.current_user_role() from public, anon, service_role;
grant execute on function public.current_user_role() to authenticated;

-- Match the production private-helper contract: only authenticated policy
-- evaluation needs EXECUTE. The function owner retains implicit privileges.
revoke all on function private.current_user_role() from public, anon, service_role;
grant execute on function private.current_user_role() to authenticated;

-- The postgres defaults are already tightened by the preceding migration.
-- Supabase also creates public objects as supabase_admin in some environments;
-- revoke only anon/authenticated defaults there, preserving service_role.
do $defaults$
begin
  if exists (select 1 from pg_roles where rolname = 'supabase_admin') then
    execute 'alter default privileges for role supabase_admin in schema public revoke all on tables from anon, authenticated';
    execute 'alter default privileges for role supabase_admin in schema public revoke all on sequences from anon, authenticated';
    execute 'alter default privileges for role supabase_admin in schema public revoke execute on functions from anon, authenticated';
  end if;
end;
$defaults$;
