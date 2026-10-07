-- Allow the Android counter to add customers as guest employees only.
-- Guest creation is restricted to authenticated Admin/Operator users.

create or replace function public.create_guest_employee(
  p_name text,
  p_phone text default null,
  p_department text default null
)
returns public.employees
language plpgsql
security definer
set search_path = public
as $$
declare
  v_guest_code text;
  v_employee public.employees;
begin
  if private.current_user_role() not in ('ADMIN','OPERATOR') then
    raise exception 'Not authorized';
  end if;

  if nullif(trim(p_name), '') is null then
    raise exception 'Guest name is required';
  end if;

  v_guest_code := public.next_guest_code();

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
$$;

revoke all on function public.create_guest_employee(text,text,text) from public;
grant execute on function public.create_guest_employee(text,text,text) to authenticated;
