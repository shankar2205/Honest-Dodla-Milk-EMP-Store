create or replace function public.next_guest_code()
returns text
language plpgsql
security definer
set search_path = public
as $$
declare n integer;
begin
  select coalesce(max(nullif(regexp_replace(guest_code, '^GUEST', ''), '')::integer),0)+1
  into n from public.employees where guest_code like 'GUEST%';
  return 'GUEST' || lpad(n::text, 3, '0');
end;
$$;
