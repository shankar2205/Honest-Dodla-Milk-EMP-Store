-- Guest-code generation mutates a sequence and inspects employee rows; it is an internal helper,
-- not a public/anonymous API. Guest creation calls it from the SECURITY DEFINER RPC.
revoke all on function public.next_guest_code() from public;
revoke all on function public.next_guest_code() from anon;
grant execute on function public.next_guest_code() to authenticated;

-- The sequence should not be directly consumable or resettable by anonymous clients.
revoke all on sequence public.guest_code_seq from anon;
