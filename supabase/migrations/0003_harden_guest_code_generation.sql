-- Keep guest-code generation ahead of existing records and serialize concurrent registrations.
-- This is intentionally the same safe generator fix applied to the live Supabase project.
CREATE OR REPLACE FUNCTION public.next_guest_code()
RETURNS text
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
DECLARE
  v_max_guest_number bigint;
  v_sequence_last bigint;
  v_sequence_called boolean;
  v_next_number bigint;
BEGIN
  PERFORM pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('public.next_guest_code', 0)
  );

  SELECT COALESCE(
    max(substring(e.guest_code FROM '^GUEST-([0-9]+)$')::bigint),
    0
  )
  INTO v_max_guest_number
  FROM public.employees AS e
  WHERE e.guest_code ~ '^GUEST-[0-9]+$';

  SELECT last_value, is_called
  INTO v_sequence_last, v_sequence_called
  FROM public.guest_code_seq;

  IF v_max_guest_number > v_sequence_last
     OR (v_max_guest_number > 0 AND NOT v_sequence_called
         AND v_max_guest_number >= v_sequence_last) THEN
    PERFORM pg_catalog.setval(
      'public.guest_code_seq'::pg_catalog.regclass,
      GREATEST(v_max_guest_number, v_sequence_last),
      true
    );
  END IF;

  v_next_number := pg_catalog.nextval(
    'public.guest_code_seq'::pg_catalog.regclass
  );

  IF v_next_number <= v_max_guest_number THEN
    PERFORM pg_catalog.setval(
      'public.guest_code_seq'::pg_catalog.regclass,
      v_max_guest_number,
      true
    );
    v_next_number := pg_catalog.nextval(
      'public.guest_code_seq'::pg_catalog.regclass
    );
  END IF;

  RETURN 'GUEST-' || lpad(v_next_number::text, 4, '0');
END;
$function$;
