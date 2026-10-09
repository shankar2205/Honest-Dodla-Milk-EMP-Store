-- Keep the guest-code sequence ahead of all existing guest identifiers.
-- This is safe to re-run and never lowers the sequence, so deleted records
-- cannot cause a previously issued GUEST-#### code to be reused.
do $$
declare
  v_max_guest_number bigint;
  v_sequence_last bigint;
  v_sequence_called boolean;
begin
  select coalesce(
    max(substring(guest_code from '^GUEST-([0-9]+)$')::bigint),
    0
  )
  into v_max_guest_number
  from public.employees
  where guest_code ~ '^GUEST-[0-9]+$';

  select last_value, is_called
  into v_sequence_last, v_sequence_called
  from public.guest_code_seq;

  if v_max_guest_number > v_sequence_last
     or (v_max_guest_number > 0 and not v_sequence_called) then
    perform setval(
      'public.guest_code_seq',
      greatest(v_max_guest_number, v_sequence_last),
      true
    );
  end if;
end;
$$;
