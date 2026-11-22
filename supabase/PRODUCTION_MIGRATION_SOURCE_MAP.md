# Production migration source map

Repository: `shankar2205/Honest-Dodla-Milk-EMP-Store`  
Branch: `rebuild/backend-foundation-2026-10-23`  
Prepared: 2026-11-22

## Purpose and evidence rules

The live Supabase migration ledger contains seven timestamped versions, while the repository contains legacy numbered migrations with duplicate prefixes. On 2026-11-22, a read-only query recovered every stored SQL statement directly from `supabase_migrations.schema_migrations.statements`. This proves what SQL is recorded in the production ledger; it does not by itself prove that the current live catalog has not drifted since those statements ran.

- **Historical SQL recovered** means a Git commit contains the relevant SQL body or grant patch.
- **Candidate mapping** means the filename/message is consistent with a ledger entry, but the applied timestamped SQL was not recovered.
- **Unresolved** means there is not enough evidence to map the ledger entry to an exact source file.
- None of these statuses authorizes production changes.

## Ledger-to-source matrix

| Production version | Ledger name | Best available source evidence | Status / remaining proof |
|---|---|---|---|
| `20261007100518` | `add_guest_employee_creator` | Exact stored statement recovered from the production ledger. It creates `create_guest_employee` with `public.current_user_role()` and `search_path=public`, then revokes PUBLIC and grants authenticated. | **Ledger SQL verified.** This is an earlier intermediate version; it is not the current production function body after the next migration. |
| `20261007100537` | `fix_guest_employee_creator_role` | Exact stored statement recovered. It replaces `public.current_user_role()` with `private.current_user_role()` in the guest-creation function while retaining the same `search_path=public` and authenticated grant. The current branch's `0004_guest_employee_creator.sql` matches this later role-helper form. | **Ledger SQL verified; candidate mapping confirmed semantically.** |
| `20261007100634` | `restrict_guest_employee_creator_rpc` | Exact stored statement matches [`0005_restrict_guest_employee_creator.sql`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/blob/rebuild/backend-foundation-2026-10-23/supabase/migrations/0005_restrict_guest_employee_creator.sql): revoke anon/PUBLIC and grant authenticated. | **Ledger SQL and candidate mapping verified.** |
| `20261007102015` | `add_counter_employee_registration` | Exact stored statement recovered: creates `create_employee` with non-empty employee-code validation (not six-digit validation), duplicate-code check, `search_path=public`, and authenticated-only execution. The repository's `0006` is grant-only; `0007_enforce_six_digit_employee_codes.sql` contains a different six-digit implementation plus legacy data updates. | **Ledger SQL verified; no exact single-file source match.** Do not replay the data-normalizing `0007` file against production. |
| `20261009101008` | `harden_guest_code_generation` | Exact stored function body recovered; it matches [`0003_harden_guest_code_generation.sql`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/blob/rebuild/backend-foundation-2026-10-23/supabase/migrations/0003_harden_guest_code_generation.sql) in behavior, including advisory locking, sequence reconciliation, and collision fallback. | **Ledger SQL and candidate mapping verified.** Current production ACL is a separate concern: anon still has EXECUTE until the proposed restriction migration is deployed. |
| `20261012111425` | `operator_daily_litres_kpi` | Candidate: [`0009_operator_daily_litres_kpi.sql`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/blob/rebuild/backend-foundation-2026-10-23/supabase/migrations/0009_operator_daily_litres_kpi.sql). [Commit `7497cec`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/7497cecbff1dddf89beffd4bf23285403acba8c9) contains the KPI function body. | **Historical function body recovered; exact ledger mapping unproven.** Check role-helper qualification, return type, time-window boundaries, and live function definition. |
| `20261012112124` | `fix_operator_daily_litres_kpi_role_check` | Exact stored statement recovered. It replaces the helper call with an active-profile check on `profiles.id=auth.uid()` and ADMIN/OPERATOR role, preserves the time-window and aggregation logic, revokes PUBLIC/anon, grants authenticated, and reloads PostgREST. | **Ledger SQL verified.** This is the authoritative source for the production KPI role check; the legacy `0009` file is only the prior intermediate implementation. |

## Verification gates before replay

1. **Completed 2026-11-22:** recover all seven stored SQL statements from `supabase_migrations.schema_migrations.statements` using SELECT only. Each ledger row contains one statement; no rollback statements are stored.
2. Capture production definitions and ACLs read-only: `pg_get_functiondef`, `pg_proc.proconfig`, effective function privileges, triggers, policies, views, constraints, indexes, and sequence state. Most targeted function/ACL details have already been captured; finish any remaining catalog objects.
3. Compare the stored statements to historical candidate files and record semantic differences. The important confirmed gaps are the original `create_employee` SQL (ledger permits any non-empty code; the candidate six-digit version is different and must not bring along legacy row updates) and the KPI role-check correction in the final ledger version.
4. Build a replay harness only from a reviewed schema-only baseline and verified forward migrations. Do not replay `0001_initial_schema.sql` or data-normalizing legacy files against production.
5. Run the replay and catalog comparison in a disposable PostgreSQL/Supabase-compatible database. Keep production read-only until a separate deployment plan is approved.

## Current conclusion

All seven exact SQL statements recorded in the production migration ledger have now been recovered. The source-to-ledger comparison is substantially stronger: guest creation's intermediate role-helper change, guest RPC grants, guest-code generator, and both KPI versions are understood. The original `create_employee` SQL is also known from the ledger and is materially different from the six-digit legacy candidate. The ledger statements are authoritative evidence of what was recorded as applied, while the live catalog fingerprint remains necessary to detect later drift. No production changes were made.


## Read-only live function comparison (2026-10-27)

A catalog-only `SELECT` captured `pg_get_functiondef`, `proconfig`, `SECURITY DEFINER`, and effective EXECUTE checks for the current live functions. No production DDL, grants, data, or migration-ledger records were changed.

| Live object | Observed production behavior | Source-map implication |
|---|---|---|
| `public.create_guest_employee(text,text,text)` | SECURITY DEFINER; `search_path=public`; checks `private.current_user_role()`; validates name; calls `public.next_guest_code()`; inserts a GUEST employee. | The function body is semantically consistent with historical `0004` / commit `bfcefa3`, but this is not byte-level proof of the timestamped applied migration. The branch's empty-search-path hardening remains a separate proposed change. |
| `public.create_employee(text,text,text,text)` | SECURITY DEFINER; `search_path=public`; checks `private.current_user_role()`; validates code/name; rejects case-insensitive duplicate employee codes; inserts EMPLOYEE row. | The live body is now documented in this observation, but the exact original creation migration has not yet been recovered. The `0006` file only changes grants and cannot stand in for the creation SQL. |
| `public.next_guest_code()` | SECURITY DEFINER=false; empty search path; advisory transaction lock; sequence reconciliation against existing `GUEST-####` values; fallback collision check. Effective EXECUTE is true for both `anon` and `authenticated`. | The body closely matches the historical implementation in `0003` / commit `a0dfc78`. The live anon grant remains a distinct exposure; branch migration `20261026000000_restrict_guest_code_generator.sql` proposes to revoke it. |
| `public.operator_daily_sales(timestamptz,timestamptz)` | SECURITY DEFINER; empty search path; role check directly queries active `public.profiles` for `auth.uid()` and ADMIN/OPERATOR role; validates the time window; groups sales by product and variant. Effective EXECUTE is false for anon and true for authenticated. | The live role check matches the exact stored SQL for ledger version `20261012112124`, which directly checks the caller's active profile. The earlier `0009` candidate corresponds to the prior ledger version and is not the final production definition. |
| `public.find_guest_by_phone(text)` | Not returned by the live function catalog query. | The legacy `0008` candidate must not be assumed to exist in production. This confirms the need to compare object presence as well as source history. |

The live `private.current_user_role()` definition is SECURITY DEFINER with an empty search path and returns the active profile role for `auth.uid()`. Its EXECUTE check is false for anon and true for authenticated.

### Next engineering step

The stored migration SQL is now recovered. Next, build a clean disposable baseline from a schema-only production snapshot, then replay only the reviewed forward migrations and rerun the catalog fingerprint. Keep `find_guest_by_phone` explicitly classified as a branch-added RPC because it is absent from the live production function catalog. Preserve `operator_daily_sales` from production. Do not apply the six-digit table constraint or data-normalizing legacy migration, and keep the four legacy employee records and their transactions unchanged. No deployment is implied.
