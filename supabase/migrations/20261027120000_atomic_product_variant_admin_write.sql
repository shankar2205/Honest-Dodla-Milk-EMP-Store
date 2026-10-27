-- Keep product + sellable variant writes atomic for the admin web.
-- This function is SECURITY INVOKER: table RLS still governs every write.
-- Product identity is resolved on the server so a failed variant write rolls back
-- any newly-created product instead of leaving a partial catalog entry.

create or replace function public.save_product_variant(
  p_variant_id uuid,
  p_product_name text,
  p_variant_name text,
  p_unit_volume_ml integer,
  p_price numeric
)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $function$
declare
  v_product_id uuid;
  v_matching_products integer;
  v_variant_id uuid;
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if coalesce(private.current_user_role()::text, '') <> 'ADMIN' then
    raise exception 'Administrator access required' using errcode = '42501';
  end if;

  if nullif(trim(coalesce(p_product_name, '')), '') is null then
    raise exception 'Product name is required' using errcode = '22023';
  end if;

  if nullif(trim(coalesce(p_variant_name, '')), '') is null then
    raise exception 'Variant name is required' using errcode = '22023';
  end if;

  if p_unit_volume_ml is null or p_unit_volume_ml < 1 then
    raise exception 'Unit volume must be a positive whole number of millilitres' using errcode = '22023';
  end if;

  if p_price is null or p_price < 0 or p_price::text = 'NaN' then
    raise exception 'Price must be a non-negative number' using errcode = '22023';
  end if;

  select count(*)
    into v_matching_products
  from public.products p
  where lower(trim(p.name)) = lower(trim(p_product_name));

  if v_matching_products > 1 then
    raise exception 'More than one product matches this name; resolve duplicate catalog entries first' using errcode = '22023';
  elsif v_matching_products = 1 then
    select p.id
      into v_product_id
    from public.products p
    where lower(trim(p.name)) = lower(trim(p_product_name))
    limit 1;
  else
    insert into public.products (name)
    values (trim(p_product_name))
    returning id into v_product_id;
  end if;

  if p_variant_id is null then
    insert into public.product_variants (
      product_id, variant_name, unit_volume_ml, price
    )
    values (
      v_product_id, trim(p_variant_name), p_unit_volume_ml, p_price
    )
    returning id into v_variant_id;
  else
    update public.product_variants
    set product_id = v_product_id,
        variant_name = trim(p_variant_name),
        unit_volume_ml = p_unit_volume_ml,
        price = p_price
    where id = p_variant_id
    returning id into v_variant_id;

    if v_variant_id is null then
      raise exception 'Product variant was not found' using errcode = 'P0002';
    end if;
  end if;

  return v_variant_id;
end;
$function$;

revoke all on function public.save_product_variant(uuid, text, text, integer, numeric) from public, anon;
grant execute on function public.save_product_variant(uuid, text, text, integer, numeric) to authenticated;
