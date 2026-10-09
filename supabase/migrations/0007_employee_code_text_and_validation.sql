-- Keep employee identifiers as text so leading zeroes are preserved.
-- This is safe for six-digit codes such as 001234, which must not become 1234.
alter table public.employees
  alter column employee_code type text
  using employee_code::text;

-- Enforce the six-ASCII-digit format for new and changed employee codes.
-- NOT VALID avoids blocking deployment if older records need manual review;
-- PostgreSQL still enforces this rule for new inserts/updates.
do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'employees_employee_code_six_digits_check'
      and conrelid = 'public.employees'::regclass
  ) then
    alter table public.employees
      add constraint employees_employee_code_six_digits_check
      check (employee_code is null or employee_code ~ '^[0-9]{6}$')
      not valid;
  end if;
end
$$;
