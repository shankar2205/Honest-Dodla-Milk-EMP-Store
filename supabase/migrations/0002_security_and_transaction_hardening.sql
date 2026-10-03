-- Honest Milk - Dodla Employee Store
-- Security and transaction integrity hardening

create schema if not exists private;

create sequence if not exists public.guest_code_seq;

do $$
declare
  max_guest integer;
begin
  select max(substring(guest_code from 7)::integer)
    into max_guest
  from public.employees
  where guest_code ~ '^GUEST-[0-9]+$';

  if max_guest is not null then
    perform setval('public.guest_code_seq', max_guest, true);
  end if;
end;
$$;

create or replace function public.next_guest_code()
returns text
language sql
set search_path = ''
as $$
  select 'GUEST-' || lpad(nextval('public.guest_code_seq')::text, 4, '0');
$$;

revoke all on function public.next_guest_code() from public;
grant execute on function public.next_guest_code() to authenticated;
grant usage, select on sequence public.guest_code_seq to authenticated;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create or replace function private.current_user_role()
returns public.user_role
language sql
stable
security definer
set search_path = ''
as $$
  select role
  from public.profiles
  where id = auth.uid()
    and active = true
$$;

revoke all on function private.current_user_role() from public;
grant execute on function private.current_user_role() to authenticated;

drop function if exists public.current_user_role();

drop policy if exists "admins manage employees" on public.employees;
drop policy if exists "admins manage products" on public.products;
drop policy if exists "admins manage variants" on public.product_variants;
drop policy if exists "admins manage profiles" on public.profiles;
drop policy if exists "operators can create transactions" on public.transactions;
drop policy if exists "admins and operators can read transactions" on public.transactions;

create policy "admins manage employees" on public.employees for all to authenticated
using (private.current_user_role() = 'ADMIN')
with check (private.current_user_role() = 'ADMIN');

create policy "admins manage products" on public.products for all to authenticated
using (private.current_user_role() = 'ADMIN')
with check (private.current_user_role() = 'ADMIN');

create policy "admins manage variants" on public.product_variants for all to authenticated
using (private.current_user_role() = 'ADMIN')
with check (private.current_user_role() = 'ADMIN');

create policy "admins manage profiles" on public.profiles for all to authenticated
using (private.current_user_role() = 'ADMIN')
with check (private.current_user_role() = 'ADMIN');

create policy "operators can create transactions" on public.transactions for insert to authenticated
with check (
  operator_id = auth.uid()
  and private.current_user_role() in ('ADMIN', 'OPERATOR')
);

create policy "admins and operators can read transactions" on public.transactions for select to authenticated
using (private.current_user_role() in ('ADMIN', 'OPERATOR'));

create or replace function public.set_transaction_price()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  current_price numeric;
begin
  select price
    into current_price
  from public.product_variants
  where id = new.product_variant_id
    and active = true;

  if current_price is null then
    raise exception 'Product variant is inactive or does not exist';
  end if;

  new.unit_price := current_price;
  return new;
end;
$$;

revoke all on function public.set_transaction_price() from public;

drop trigger if exists set_transaction_price on public.transactions;
create trigger set_transaction_price
before insert on public.transactions
for each row execute function public.set_transaction_price();

create index if not exists transactions_operator_id_idx
on public.transactions(operator_id);
