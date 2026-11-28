-- Centralize profile role/status changes in a guarded database function.
-- The browser must not be able to bypass self-protection or last-admin safeguards.

create or replace function public.update_profile_access(
  p_user_id uuid,
  p_role public.user_role,
  p_active boolean
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_actor uuid := auth.uid();
  v_target_role public.user_role;
  v_target_active boolean;
begin
  if v_actor is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if not exists (
    select 1
    from public.profiles p
    where p.id = v_actor
      and p.active = true
      and p.role = 'ADMIN'::public.user_role
  ) then
    raise exception 'Administrator access required' using errcode = '42501';
  end if;

  if p_user_id is null or p_role is null or p_active is null then
    raise exception 'User, role, and active status are required' using errcode = '22023';
  end if;

  if p_user_id = v_actor then
    raise exception 'You cannot change your own role or active status' using errcode = '42501';
  end if;

  -- Serialize profile access changes so concurrent requests cannot each remove
  -- a different administrator based on the same stale count.
  perform pg_catalog.pg_advisory_xact_lock(746291035);

  select p.role, p.active
    into v_target_role, v_target_active
  from public.profiles p
  where p.id = p_user_id
  for update;

  if not found then
    raise exception 'User profile was not found' using errcode = 'P0002';
  end if;

  if v_target_role = 'ADMIN'::public.user_role
     and v_target_active = true
     and (p_role <> 'ADMIN'::public.user_role or p_active = false)
     and (
       select count(*)
       from public.profiles p
       where p.role = 'ADMIN'::public.user_role
         and p.active = true
     ) <= 1 then
    raise exception 'At least one active administrator must remain' using errcode = '23514';
  end if;

  update public.profiles
  set role = p_role,
      active = p_active
  where id = p_user_id;
end;
$function$;

revoke all on function public.update_profile_access(uuid, public.user_role, boolean) from public, anon;
grant execute on function public.update_profile_access(uuid, public.user_role, boolean) to authenticated;

-- Force the Admin Web to use the guarded RPC rather than direct profile updates.
revoke update on table public.profiles from authenticated;
