-- Restore the guest-phone lookup omitted from the timestamped production migration history.
-- Keep guest-code generation behind SECURITY DEFINER creation RPCs.

create or replace function public.find_guest_by_phone(p_phone text)
returns setof public.employees
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_employee public.employees;
  v_digits text;
begin
  if coalesce(private.current_user_role()::text, '') not in ('ADMIN', 'OPERATOR') then
    raise exception 'Not authorized';
  end if;

  v_digits := pg_catalog.regexp_replace(coalesce(p_phone, ''), '[^0-9]', '', 'g');
  if v_digits = '' then
    return;
  end if;

  select e.*
    into v_employee
  from public.employees e
  where e.active = true
    and e.id_type = 'GUEST'
    and pg_catalog.regexp_replace(coalesce(e.phone, ''), '[^0-9]', '', 'g') = v_digits
  order by e.created_at desc nulls last
  limit 1;

  if found then
    return next v_employee;
  end if;
  return;
end;
$function$;

revoke all on function public.find_guest_by_phone(text) from public;
revoke all on function public.find_guest_by_phone(text) from anon;
grant execute on function public.find_guest_by_phone(text) to authenticated;

-- Admin UI may retain a manually entered guest identifier, but the write must be
-- validated and performed inside the database rather than through a table insert.
create or replace function public.create_guest_employee_with_code(
  p_name text,
  p_phone text default null,
  p_department text default null,
  p_guest_code text default null
)
returns public.employees
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_guest_code text;
  v_employee public.employees;
begin
  if coalesce(private.current_user_role()::text, '') not in ('ADMIN', 'OPERATOR') then
    raise exception 'Not authorized';
  end if;

  if nullif(trim(coalesce(p_name, '')), '') is null then
    raise exception 'Guest name is required';
  end if;

  if nullif(trim(coalesce(p_guest_code, '')), '') is null then
    v_guest_code := public.next_guest_code();
  else
    v_guest_code := upper(trim(p_guest_code));
    if v_guest_code !~ '^[A-Z0-9-]{1,32}$' then
      raise exception 'Guest ID contains invalid characters';
    end if;
    if exists (
      select 1 from public.employees e
      where lower(e.guest_code) = lower(v_guest_code)
    ) then
      raise exception 'Guest ID already exists';
    end if;
  end if;

  insert into public.employees (
    guest_code, id_type, name, department, phone, active
  )
  values (
    v_guest_code,
    'GUEST',
    trim(p_name),
    nullif(trim(coalesce(p_department, '')), ''),
    nullif(trim(coalesce(p_phone, '')), ''),
    true
  )
  returning * into v_employee;

  return v_employee;
end;
$function$;

revoke all on function public.create_guest_employee_with_code(text,text,text,text) from public;
revoke all on function public.create_guest_employee_with_code(text,text,text,text) from anon;
grant execute on function public.create_guest_employee_with_code(text,text,text,text) to authenticated;
