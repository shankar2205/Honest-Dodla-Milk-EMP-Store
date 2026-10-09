-- Employee codes are identifiers, so keep the column as text to preserve leading zeroes.
-- Normalize legacy EMP-prefixed values when the six-digit mapping is unambiguous.
update public.employees
set employee_code = regexp_replace(employee_code, '^EMP', '', 'i')
where employee_code ~* '^EMP[0-9]{6}$';

-- Legacy five-digit EMP-prefixed values are left-padded to the required six digits.
update public.employees
set employee_code = lpad(regexp_replace(employee_code, '^EMP', '', 'i'), 6, '0')
where employee_code ~* '^EMP[0-9]{5}$';

create or replace function public.create_employee(
  p_employee_code text,
  p_name text,
  p_phone text default null,
  p_department text default null
)
returns public.employees
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_employee public.employees;
begin
  if private.current_user_role() not in ('ADMIN','OPERATOR') then
    raise exception 'Not authorized';
  end if;

  if trim(coalesce(p_employee_code, '')) !~ '^[0-9]{6}$' then
    raise exception 'Employee code must contain exactly 6 numeric digits';
  end if;

  if nullif(trim(p_name), '') is null then
    raise exception 'Employee name is required';
  end if;

  if exists (
    select 1 from public.employees
    where lower(employee_code) = lower(trim(p_employee_code))
  ) then
    raise exception 'Employee ID already exists';
  end if;

  insert into public.employees(employee_code, id_type, name, department, phone, active)
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

revoke execute on function public.create_employee(text,text,text,text) from anon;
revoke execute on function public.create_employee(text,text,text,text) from public;
grant execute on function public.create_employee(text,text,text,text) to authenticated;
