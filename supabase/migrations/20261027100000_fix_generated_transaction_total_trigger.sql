-- Production uses a generated transactions.total_amount column.
-- Keep the price trigger focused on setting the authoritative catalog price; PostgreSQL
-- computes total_amount from quantity * unit_price after BEFORE triggers complete.

create or replace function public.set_transaction_price()
returns trigger
language plpgsql
set search_path = ''
as $function$
declare
  current_price numeric;
begin
  select pv.price
    into current_price
  from public.product_variants pv
  where pv.id = new.product_variant_id
    and pv.active = true;

  if current_price is null then
    raise exception 'Product variant is inactive or does not exist';
  end if;

  new.unit_price := current_price;
  return new;
end;
$function$;

drop trigger if exists set_transaction_price on public.transactions;
create trigger set_transaction_price
before insert on public.transactions
for each row execute function public.set_transaction_price();
