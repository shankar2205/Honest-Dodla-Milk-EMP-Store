-- Securely check for an existing active guest by normalized phone number.
-- Counter operators cannot select the employees table directly under RLS.

create or replace function public.find_guest_by_phone(p_phone text)
returns public.employees
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_employee public.employees;
  v_digits text;
begin
  if private.current_user_role() not in ('ADMIN', 'OPERATOR') then
    raise exception 'Not authorized';
  end if;

  v_digits := regexp_replace(coalesce(p_phone, ''), '[^0-9]', '', 'g');
  if v_digits = '' then
    return null;
  end if;

  select e.*
    into v_employee
  from public.employees e
  where e.active = true
    and e.id_type = 'GUEST'
    and regexp_replace(coalesce(e.phone, ''), '[^0-9]', '', 'g') = v_digits
  order by e.created_at desc nulls last
  limit 1;

  return v_employee;
end;
$$;

revoke all on function public.find_guest_by_phone(text) from public;
grant execute on function public.find_guest_by_phone(text) to authenticated;
