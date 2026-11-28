-- Reconcile the application RLS role helper with the hardened private helper.
-- Keep the public helper available to authenticated legacy clients, but remove
-- PUBLIC/anon access. All policies use private.current_user_role() consistently.
-- Default privileges for application-owned public objects are hardened by the preceding migration.

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
DO $legacy_helper$
BEGIN
  IF to_regprocedure('public.current_user_role()') IS NOT NULL THEN
    EXECUTE 'REVOKE ALL ON FUNCTION public.current_user_role() FROM PUBLIC, anon, service_role';
    EXECUTE 'GRANT EXECUTE ON FUNCTION public.current_user_role() TO authenticated';
  END IF;
END;
$legacy_helper$;

-- Match the production private-helper contract: only authenticated policy
-- evaluation needs EXECUTE. The function owner retains implicit privileges.
revoke all on function private.current_user_role() from public, anon, service_role;
grant execute on function private.current_user_role() to authenticated;

-- Leave supabase_admin defaults unchanged; they are platform-managed.
