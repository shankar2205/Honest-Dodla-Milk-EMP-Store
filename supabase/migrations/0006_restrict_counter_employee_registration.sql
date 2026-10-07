-- Restrict counter employee registration RPC to authenticated users.
revoke execute on function public.create_employee(text,text,text,text) from anon;
revoke execute on function public.create_employee(text,text,text,text) from public;
grant execute on function public.create_employee(text,text,text,text) to authenticated;
