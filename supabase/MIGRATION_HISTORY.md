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
