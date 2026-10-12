-- Operator-safe daily sales KPI. Inputs are local-day boundaries converted to timestamptz by the client.
-- Returns one row per product variant; the Android counter computes the daily grand total.
create or replace function public.operator_daily_sales(
  p_start timestamptz,
  p_end timestamptz
)
returns table (
  product_name text,
  variant_name text,
  quantity_sold bigint,
  total_litres numeric,
  transaction_count bigint
)
language plpgsql
security definer
set search_path = ''
as $$
begin
  if coalesce(public.current_user_role()::text, '') not in ('ADMIN', 'OPERATOR') then
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
$$;

revoke all on function public.operator_daily_sales(timestamptz, timestamptz) from public, anon;
grant execute on function public.operator_daily_sales(timestamptz, timestamptz) to authenticated;
