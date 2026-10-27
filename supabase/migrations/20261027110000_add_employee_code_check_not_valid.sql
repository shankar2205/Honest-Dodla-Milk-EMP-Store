-- Production has legacy employee codes that do not match the six-digit format.
-- Add the guard as NOT VALID: existing rows remain untouched, while new/changed rows
-- must satisfy the rule. Validate only after legacy identifiers have been reviewed.
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
