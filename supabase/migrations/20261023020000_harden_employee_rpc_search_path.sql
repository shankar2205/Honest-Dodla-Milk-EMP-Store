-- Harden SECURITY DEFINER employee-creation RPCs against search_path object shadowing.
-- Keep the existing authorization and validation behavior; fully qualify referenced objects.

create or replace function public.create_employee(
  p_employee_code text,
  p_name text,
  p_phone text default null,
  p_department text default null
)
returns public.employees
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_employee public.employees;
begin
  if private.current_user_role() not in ('ADMIN', 'OPERATOR') then
    raise exception 'Not authorized';
  end if;

  if nullif(trim(p_employee_code), '') is null then
    raise exception 'Employee ID is required';
  end if;

  if nullif(trim(p_name), '') is null then
    raise exception 'Employee name is required';
  end if;

  if exists (
    select 1
    from public.employees
    where lower(employee_code) = lower(trim(p_employee_code))
  ) then
    raise exception 'Employee ID already exists';
  end if;

  insert into public.employees (
    employee_code, id_type, name, department, phone, active
  )
  values (
    trim(p_employee_code),
    'EMPLOYEE',
    trim(p_name),
    nullif(trim(coalesce(p_department, '')), ''),
    nullif(trim(coalesce(p_phone, '')), ''),
    true
  )
  returning * into v_employee;

  return v_employee;
end;
$function$;

create or replace function public.create_guest_employee(
  p_name text,
  p_phone text default null,
  p_department text default null
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
  if private.current_user_role() not in ('ADMIN', 'OPERATOR') then
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
$function$;
