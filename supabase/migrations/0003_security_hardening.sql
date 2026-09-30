-- Security hardening for reporting views.
-- The report view must respect the underlying table RLS policies instead of
-- exposing transaction rows through the view owner.
alter view public.transaction_report set (security_invoker = true);

-- Explicitly prevent anonymous clients from reading the reporting view.
revoke all on table public.transaction_report from anon;
grant select on table public.transaction_report to authenticated;

-- Keep the role helper intentionally narrow: it returns only the active
-- caller's role and does not expose profile rows.
revoke all on function public.current_user_role() from public;
grant execute on function public.current_user_role() to authenticated;


-- Make the profile self-read policy explicit about active accounts.
drop policy if exists "users can read their own profile" on public.profiles;
create policy "users can read their own profile" on public.profiles
for select to authenticated
using (id = auth.uid() and active = true);
