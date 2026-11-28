# Supabase migration history notes

## Why the legacy migration filenames are not being renamed automatically

The numbered SQL files in this directory are not interchangeable duplicates. They contain distinct schema, function, policy, grant, and data changes:

- `0001_initial_schema.sql` creates the core schema.
- The three `0002_*.sql` files create/secure guest-code generation, harden transaction/security helpers, and synchronize the guest-code sequence.
- The two `0003_*.sql` files harden guest-code generation and reporting/RLS behavior.
- `0004`–`0006` restrict guest/customer registration RPCs.
- The two `0007_*.sql` files convert employee codes to text and enforce six-digit validation, including legacy-data normalization.
- `0008` adds secure guest-phone lookup.
- `0009` adds operator daily-sales reporting.

Supabase CLI migration versions are derived from the filename prefix before the first underscore. Therefore, the repeated `0002`, `0003`, and `0007` prefixes are ambiguous for migration-history tracking and local replay.

## Safety rule

Do not simply rename these files to unique timestamps and do not run `supabase db push` against production while the local numbered history is unreconciled. Renaming changes the migration versions the CLI compares with the remote migration history; it does not mark the renamed migrations as already applied.

The production project currently has its own recorded timestamped migration history. The legacy numbered files have not been reconciled against that history as a verified baseline in this repository. Some migrations also mutate existing employee data, so replay order matters.

## Safe reconciliation procedure

1. Obtain a schema-only snapshot of the current production schema and a read-only export of `supabase_migrations.schema_migrations` (no production DDL or data writes).
2. Compare every legacy migration's effects with the current schema and recorded remote versions; identify which effects are already present and which are still required for a clean local rebuild.
3. Create a fresh local baseline from the verified schema snapshot, then apply only the remaining migrations in a unique, chronological order on a disposable local PostgreSQL/Supabase instance.
4. Run local reset and migration assertions, including RPC grants, RLS policies, and employee-code constraints.
5. Only then decide whether to archive legacy files or replace them with the validated baseline plus timestamped forward migrations. Keep production migration history unchanged unless a separately reviewed deployment plan explicitly authorizes a change.

## Current status

This note documents a known local migration-history blocker. It does not modify database schema, data, grants, policies, or production migration records. New forward migrations in this branch remain unapplied.


## Validation update — 2026-11-28

This section supersedes the earlier statement above that the forward migrations remain unapplied. The following validation has now been completed on disposable local databases only:

- A schema-only production snapshot was exported, checksum-verified, restored into a fresh database, and its structural fingerprint compared with the source snapshot.
- The seven production migration-ledger records were exported from the restored full backup and reviewed; production migration history was not changed.
- All ten timestamped forward migrations in this branch were replayed in chronological order against the fresh schema-only baseline. Migrations 1–9 completed as `postgres`. Migration 10 initially stopped at the `supabase_admin` default-privilege block because the local `postgres` role is not a member of `supabase_admin`; the complete migration then succeeded when rerun as the local `supabase_admin` superuser.
- Post-replay checks confirmed five application tables have RLS enabled and two policies each; the expected eight RPCs exist with empty search paths; and the restricted RPCs have the expected authenticated-only grants.
- The schema-only archive was created with `--no-acl`. Consequently, its restored function ACLs do not preserve production ACLs: `create_employee` and `operator_daily_sales` show inherited PUBLIC EXECUTE in this replay. Do not use those two effective-grant results as production ACL evidence.
- A separate full-production-backup ACL test, with production-like default privileges seeded, confirmed migrations 9 and 10 remove anon/authenticated defaults while preserving service_role defaults. The four legacy employee records and their 69 linked transactions remained unchanged in the full-data validation database.
- GitHub Actions for Backend Catalog Reconciliation Tests, Backend Database Tests, Admin Web Build, and Android Counter Build passed on PR head `e2c53eb77b81b1b51e0f0f4ba5d39c6b99b22d9e`.

### Remaining migration-history and release safeguards

- The ten forward migrations have been validated as a disposable replay chain; this does not reconcile or rewrite the seven historical production ledger records and does not authorize `supabase db push`.
- **Before production execution, verify the migration runner's effective authority to alter default privileges for objects created by `supabase_admin`.** The local replay proved that a non-member `postgres` role cannot alter those defaults; rerunning as `supabase_admin` succeeded. Do not assume the production runner has equivalent membership or superuser authority.
- Preserve the four legacy employee codes and their linked transactions. Do not replay `0007_enforce_six_digit_employee_codes.sql` or add a table-level six-digit constraint.
- Production remains unchanged. No merge, deployment, production migration, or production write is authorized by this validation.
