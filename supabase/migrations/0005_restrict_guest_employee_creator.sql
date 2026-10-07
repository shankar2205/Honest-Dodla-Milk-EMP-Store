-- Restrict guest employee creation RPC to signed-in counter users.
revoke execute on function public.create_guest_employee(text,text,text) from anon;
revoke execute on function public.create_guest_employee(text,text,text) from public;
grant execute on function public.create_guest_employee(text,text,text) to authenticated;
