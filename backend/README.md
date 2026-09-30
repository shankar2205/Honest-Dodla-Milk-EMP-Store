# Backend/API layer

The first production backend contract is intentionally kept close to Supabase/PostgreSQL. Clients should use authenticated Supabase access for CRUD and transaction operations, with database RLS enforcing roles.

Planned API/domain modules:
- auth
- employees
- products
- transactions
- reports

The transaction write path must always use the authenticated operator identity and database-generated timestamp.
