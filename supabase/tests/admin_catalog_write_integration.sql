-- Integration test for the guarded admin catalog write RPCs.
-- Runs only against the disposable CI database; all fixture changes are rolled back.

begin;

set local request.jwt.claim.sub = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
set local role authenticated;

do $admin_catalog_rpc_test$
begin
  perform public.update_employee_details(
    '22222222-2222-4222-8222-222222222222',
    'Updated Test Employee',
    'CI Department',
    '9000000001'
  );
  if not exists (
    select 1 from public.employees
    where id = '22222222-2222-4222-8222-222222222222'
      and name = 'Updated Test Employee'
      and department = 'CI Department'
      and phone = '9000000001'
      and employee_code = '000001'
  ) then
    raise exception 'update_employee_details did not update only the intended fields';
  end if;

  perform public.set_employee_active(
    '22222222-2222-4222-8222-222222222222', false
  );
  if (select active from public.employees
      where id = '22222222-2222-4222-8222-222222222222') then
    raise exception 'set_employee_active did not deactivate the employee';
  end if;
  perform public.set_employee_active(
    '22222222-2222-4222-8222-222222222222', true
  );

  perform public.set_product_variant_active(
    '44444444-4444-4444-8444-444444444444', false
  );
  if (select active from public.product_variants
      where id = '44444444-4444-4444-8444-444444444444') then
    raise exception 'set_product_variant_active did not deactivate the variant';
  end if;
  perform public.set_product_variant_active(
    '44444444-4444-4444-8444-444444444444', true
  );

  -- Direct table writes must be denied even to an authenticated administrator.
  begin
    update public.employees
    set active = false
    where id = '22222222-2222-4222-8222-222222222222';
    raise exception 'direct employee UPDATE unexpectedly succeeded';
  exception when insufficient_privilege then
    null;
  end;

  begin
    update public.product_variants
    set active = false
    where id = '44444444-4444-4444-8444-444444444444';
    raise exception 'direct product-variant UPDATE unexpectedly succeeded';
  exception when insufficient_privilege then
    null;
  end;
end;
$admin_catalog_rpc_test$;

reset role;
set local request.jwt.claim.sub = '11111111-1111-4111-8111-111111111111';
set local role authenticated;

do $operator_denied_test$
begin
  begin
    perform public.update_employee_details(
      '22222222-2222-4222-8222-222222222222',
      'Operator Must Not Edit',
      null,
      null
    );
    raise exception 'operator unexpectedly edited employee details';
  exception when insufficient_privilege then
    null;
  end;

  begin
    perform public.set_employee_active(
      '22222222-2222-4222-8222-222222222222', false
    );
    raise exception 'operator unexpectedly changed employee status';
  exception when insufficient_privilege then
    null;
  end;

  begin
    perform public.set_product_variant_active(
      '44444444-4444-4444-8444-444444444444', false
    );
    raise exception 'operator unexpectedly changed product status';
  exception when insufficient_privilege then
    null;
  end;
end;
$operator_denied_test$;

rollback;

select 'Admin catalog write RPC integration tests passed' as result;
