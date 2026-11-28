-- Performance-only RLS policy tuning; preserves the existing access predicates.
-- Wrap auth.uid() in a scalar subquery so PostgreSQL can evaluate it once per statement
-- instead of once per candidate row.

drop policy if exists "users can read their own profile" on public.profiles;
create policy "users can read their own profile"
  on public.profiles
  for select
  to authenticated
  using ((id = (select auth.uid())) and active = true);

drop policy if exists "operators can create transactions" on public.transactions;
create policy "operators can create transactions"
  on public.transactions
  for insert
  to authenticated
  with check (
    operator_id = (select auth.uid())
    and private.current_user_role() = any (
      array['ADMIN'::public.user_role, 'OPERATOR'::public.user_role]
    )
  );
