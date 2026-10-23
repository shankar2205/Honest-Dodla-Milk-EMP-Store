-- Backend hardening: create a multi-item transaction atomically on the server.
-- The authenticated user's identity and product prices are derived server-side.
-- Existing tables and data are preserved.

create or replace function public.create_transaction_batch(
  p_employee_id uuid,
  p_items jsonb
)
returns table (transaction_id uuid)
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_item jsonb;
  v_variant_id uuid;
  v_quantity integer;
  v_price numeric(10,2);
  v_transaction_id uuid;
  v_item_count integer := 0;
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if not exists (
    select 1
    from public.profiles p
    where p.id = v_user_id
      and p.active = true
      and p.role in ('ADMIN'::public.user_role, 'OPERATOR'::public.user_role)
  ) then
    raise exception 'Your account is inactive or not authorized' using errcode = '42501';
  end if;

  if p_employee_id is null or not exists (
    select 1 from public.employees e
    where e.id = p_employee_id and e.active = true
  ) then
    raise exception 'Select an active employee or guest' using errcode = '22023';
  end if;

  if p_items is null or jsonb_typeof(p_items) <> 'array'
     or jsonb_array_length(p_items) = 0 then
    raise exception 'At least one item is required' using errcode = '22023';
  end if;

  if jsonb_array_length(p_items) > 100 then
    raise exception 'A transaction can contain at most 100 items' using errcode = '22023';
  end if;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    v_item_count := v_item_count + 1;

    if jsonb_typeof(v_item) <> 'object'
       or coalesce(v_item->>'variant_id', '') = ''
       or coalesce(v_item->>'quantity', '') !~ '^[0-9]+$' then
      raise exception 'Each item must include variant_id and a whole-number quantity' using errcode = '22023';
    end if;

    v_variant_id := (v_item->>'variant_id')::uuid;
    v_quantity := (v_item->>'quantity')::integer;

    if v_quantity < 1 or v_quantity > 10000 then
      raise exception 'Quantity must be between 1 and 10000' using errcode = '22023';
    end if;

    select pv.price
      into v_price
    from public.product_variants pv
    join public.products p on p.id = pv.product_id
    where pv.id = v_variant_id
      and pv.active = true
      and p.active = true;

    if not found then
      raise exception 'A selected product is inactive or no longer available' using errcode = '22023';
    end if;

    insert into public.transactions (
      employee_id, product_variant_id, quantity, unit_price, operator_id
    ) values (
      p_employee_id, v_variant_id, v_quantity, v_price, v_user_id
    )
    returning id into v_transaction_id;

    transaction_id := v_transaction_id;
    return next;
  end loop;

  return;
end;
$$;

revoke all on function public.create_transaction_batch(uuid, jsonb) from public, anon;
grant execute on function public.create_transaction_batch(uuid, jsonb) to authenticated;
