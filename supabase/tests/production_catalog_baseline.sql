-- Production-only catalog objects that must survive the candidate migration set.
-- This fixture is applied only to the disposable CI database before candidate migrations.
-- It contains no application rows and does not run against production.
create schema if not exists extensions;
alter extension pgcrypto set schema extensions;

create index if not exists transactions_operator_id_idx
  on public.transactions (operator_id);

create or replace function public.operator_daily_sales(
  p_start timestamp with time zone,
  p_end timestamp with time zone
)
returns table(
  product_name text,
  variant_name text,
  quantity_sold bigint,
  total_litres numeric,
  transaction_count bigint
)
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if not exists (
    select 1
    from public.profiles pr
    where pr.id = auth.uid()
      and pr.active is true
      and pr.role::text in ('ADMIN', 'OPERATOR')
  ) then
    raise exception 'Not authorized';
  end if;

  if p_start is null or p_end is null or p_end <= p_start then
    raise exception 'Invalid reporting window';
  end if;

  return query
  select
    p.name::text,
    pv.variant_name::text,
    sum(t.quantity)::bigint,
    round(sum(t.quantity::numeric * pv.unit_volume_ml::numeric) / 1000, 3),
    count(*)::bigint
  from public.transactions t
  join public.product_variants pv on pv.id = t.product_variant_id
  join public.products p on p.id = pv.product_id
  where t.transaction_at >= p_start
    and t.transaction_at < p_end
  group by p.name, pv.variant_name
  order by p.name, pv.variant_name;
end;
$function$;

revoke all on function public.operator_daily_sales(timestamptz, timestamptz)
  from public, anon;
grant execute on function public.operator_daily_sales(timestamptz, timestamptz)
  to authenticated, service_role;
