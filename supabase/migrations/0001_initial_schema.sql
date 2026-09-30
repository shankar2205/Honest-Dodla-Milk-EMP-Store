-- Honest Milk - Dodla Employee Store
-- Initial PostgreSQL/Supabase schema

create extension if not exists pgcrypto;

create type public.user_role as enum ('ADMIN', 'OPERATOR');
create type public.employee_id_type as enum ('EMPLOYEE', 'GUEST');

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null,
  username text unique,
  role public.user_role not null default 'OPERATOR',
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.employees (
  id uuid primary key default gen_random_uuid(),
  employee_code text unique,
  guest_code text unique,
  id_type public.employee_id_type not null,
  name text not null,
  department text,
  phone text,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint employees_identifier_check check (
    (id_type = 'EMPLOYEE' and employee_code is not null and guest_code is null)
    or (id_type = 'GUEST' and guest_code is not null and employee_code is null)
  )
);

create table public.products (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.product_variants (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete restrict,
  variant_name text not null,
  unit_volume_ml integer not null check (unit_volume_ml > 0),
  price numeric(10,2) not null check (price >= 0),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(product_id, variant_name)
);

create table public.transactions (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees(id) on delete restrict,
  product_variant_id uuid not null references public.product_variants(id) on delete restrict,
  quantity integer not null check (quantity > 0),
  unit_price numeric(10,2) not null check (unit_price >= 0),
  total_amount numeric(12,2) generated always as (quantity * unit_price) stored,
  operator_id uuid not null references public.profiles(id) on delete restrict,
  transaction_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create index employees_name_idx on public.employees (lower(name));
create index transactions_employee_date_idx on public.transactions (employee_id, transaction_at desc);
create index transactions_date_idx on public.transactions (transaction_at desc);
create index transactions_product_date_idx on public.transactions (product_variant_id, transaction_at desc);

create or replace view public.transaction_report as
select t.id, t.transaction_at,
  e.name as employee_name,
  coalesce(e.employee_code, e.guest_code) as employee_identifier,
  e.id_type, e.department,
  p.name as product_name, pv.variant_name, pv.unit_volume_ml,
  t.quantity,
  (t.quantity * pv.unit_volume_ml) as total_volume_ml,
  round((t.quantity * pv.unit_volume_ml)::numeric / 1000, 3) as total_volume_litres,
  t.unit_price, t.total_amount, pr.display_name as operator_name
from public.transactions t
join public.employees e on e.id = t.employee_id
join public.product_variants pv on pv.id = t.product_variant_id
join public.products p on p.id = pv.product_id
join public.profiles pr on pr.id = t.operator_id;

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end;
$$;

create trigger profiles_updated_at before update on public.profiles for each row execute function public.set_updated_at();
create trigger employees_updated_at before update on public.employees for each row execute function public.set_updated_at();
create trigger products_updated_at before update on public.products for each row execute function public.set_updated_at();
create trigger product_variants_updated_at before update on public.product_variants for each row execute function public.set_updated_at();

alter table public.profiles enable row level security;
alter table public.employees enable row level security;
alter table public.products enable row level security;
alter table public.product_variants enable row level security;
alter table public.transactions enable row level security;

create or replace function public.current_user_role()
returns public.user_role language sql stable security definer set search_path = public as $$
  select role from public.profiles where id = auth.uid() and active = true
$$;

create policy "authenticated users can read active employees" on public.employees
for select to authenticated using (active = true);
create policy "admins manage employees" on public.employees for all to authenticated
using (public.current_user_role() = 'ADMIN') with check (public.current_user_role() = 'ADMIN');

create policy "authenticated users read active products" on public.products
for select to authenticated using (active = true);
create policy "admins manage products" on public.products for all to authenticated
using (public.current_user_role() = 'ADMIN') with check (public.current_user_role() = 'ADMIN');

create policy "authenticated users read active variants" on public.product_variants
for select to authenticated using (active = true);
create policy "admins manage variants" on public.product_variants for all to authenticated
using (public.current_user_role() = 'ADMIN') with check (public.current_user_role() = 'ADMIN');

create policy "operators can create transactions" on public.transactions for insert to authenticated
with check (operator_id = auth.uid() and public.current_user_role() in ('ADMIN', 'OPERATOR'));
create policy "admins and operators can read transactions" on public.transactions for select to authenticated
using (public.current_user_role() in ('ADMIN', 'OPERATOR'));

create policy "users can read their own profile" on public.profiles for select to authenticated
using (id = auth.uid());
create policy "admins manage profiles" on public.profiles for all to authenticated
using (public.current_user_role() = 'ADMIN') with check (public.current_user_role() = 'ADMIN');

insert into public.products (name) values ('Honest Milk') on conflict do nothing;
insert into public.product_variants (product_id, variant_name, unit_volume_ml, price)
select p.id, '500 ML', 500, 38 from public.products p where p.name = 'Honest Milk'
and not exists (select 1 from public.product_variants v where v.product_id=p.id and v.variant_name='500 ML');
insert into public.product_variants (product_id, variant_name, unit_volume_ml, price)
select p.id, '1 L', 1000, 75 from public.products p where p.name = 'Honest Milk'
and not exists (select 1 from public.product_variants v where v.product_id=p.id and v.variant_name='1 L');
