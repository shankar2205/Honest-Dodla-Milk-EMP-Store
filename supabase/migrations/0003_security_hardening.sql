-- Security hardening for reporting views.
-- The report view must respect the underlying table RLS policies instead of
-- exposing transaction rows through the view owner.
alter view public.transaction_report set (security_invoker = true);

-- Keep the role helper intentionally narrow: it returns only the active
-- caller's role and does not expose profile rows.
revoke all on function public.current_user_role() from public;
grant execute on function public.current_user_role() to authenticated;
