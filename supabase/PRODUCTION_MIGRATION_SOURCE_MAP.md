# Production migration source map

Repository: `shankar2205/Honest-Dodla-Milk-EMP-Store`  
Branch: `rebuild/backend-foundation-2026-10-23`  
Prepared: 2026-10-27

## Purpose and evidence rules

The live Supabase migration ledger contains seven timestamped versions, while the repository contains legacy numbered migrations with duplicate prefixes. This file records the best source candidates found in Git history without asserting that a matching name or nearby commit proves byte-for-byte identity with SQL applied to production.

- **Historical SQL recovered** means a Git commit contains the relevant SQL body or grant patch.
- **Candidate mapping** means the filename/message is consistent with a ledger entry, but the applied timestamped SQL was not recovered.
- **Unresolved** means there is not enough evidence to map the ledger entry to an exact source file.
- None of these statuses authorizes production changes.

## Ledger-to-source matrix

| Production version | Ledger name | Best available source evidence | Status / remaining proof |
|---|---|---|---|
| `20261007100518` | `add_guest_employee_creator` | Candidate: [`0004_guest_employee_creator.sql`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/blob/rebuild/backend-foundation-2026-10-23/supabase/migrations/0004_guest_employee_creator.sql). Historical implementation is present in [commit `bfcefa3`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/bfcefa30e6439875453a593be1357a3c5d59523b). | **Historical SQL recovered; exact ledger mapping unproven.** Historical body uses `search_path = public`; compare against the live function definition and the applied SQL export. |
| `20261007100537` | `fix_guest_employee_creator_role` | Later guest-RPC/role-helper follow-up commits exist, but no exact applied timestamped SQL file has been tied to this version. | **Unresolved.** Recover the exact role-helper change and compare its function body, role semantics, and grants with production. |
| `20261007100634` | `restrict_guest_employee_creator_rpc` | Candidate: [`0005_restrict_guest_employee_creator.sql`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/blob/rebuild/backend-foundation-2026-10-23/supabase/migrations/0005_restrict_guest_employee_creator.sql); [commit `481e2a2`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/481e2a22761805a27fc8848de7cc42b27b64a48f) explicitly revokes anon/public and grants authenticated execution. | **Historical grant patch recovered; exact ledger mapping unproven.** Confirm the effective production ACL and applied SQL. |
| `20261007102015` | `add_counter_employee_registration` | [Commit `d94105f`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/d94105f405612d4e90fa2dc2009cb8161912cb0b) shows client-side use of the employee registration RPC; [commit `832020f`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/832020f6ed6841f5ef7a4098448039d752d60091) records authenticated-only grants. Candidate grant file: [`0006_restrict_counter_employee_registration.sql`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/blob/rebuild/backend-foundation-2026-10-23/supabase/migrations/0006_restrict_counter_employee_registration.sql). | **RPC usage and grant history recovered; creation migration unresolved.** The grant-only file does not establish the full SQL that originally created `create_employee`. |
| `20261009101008` | `harden_guest_code_generation` | Candidate: [`0003_harden_guest_code_generation.sql`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/blob/rebuild/backend-foundation-2026-10-23/supabase/migrations/0003_harden_guest_code_generation.sql). [Commit `a0dfc78`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/a0dfc78031e04ebff382c733edffcb7e924f7c2b) contains the full advisory-lock and sequence-reconciliation generator. | **Historical function body recovered; exact ledger mapping unproven.** Verify against `pg_get_functiondef`, sequence properties, and a trusted applied-SQL export. |
| `20261012111425` | `operator_daily_litres_kpi` | Candidate: [`0009_operator_daily_litres_kpi.sql`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/blob/rebuild/backend-foundation-2026-10-23/supabase/migrations/0009_operator_daily_litres_kpi.sql). [Commit `7497cec`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/7497cecbff1dddf89beffd4bf23285403acba8c9) contains the KPI function body. | **Historical function body recovered; exact ledger mapping unproven.** Check role-helper qualification, return type, time-window boundaries, and live function definition. |
| `20261012112124` | `fix_operator_daily_litres_kpi_role_check` | [Commit `900785c`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/900785c5bb0b5c86aaa820963aee4879b2303bee) changes the KPI grant to revoke from both `public` and `anon`, then grants `authenticated`. Other historical edits changed role-helper references. | **Follow-up evidence recovered; exact ledger mapping unresolved.** The ledger name implies a role-check correction, so do not assume the grant-only patch is the whole applied migration. |

## Verification gates before replay

1. Obtain trusted SQL for the seven applied timestamped migrations (for example, an export from the deployment source or CI artifact). Git history alone currently does not prove exact mapping for every ledger row.
2. Capture production definitions and ACLs read-only: `pg_get_functiondef`, `pg_proc.proconfig`, effective function privileges, triggers, policies, views, constraints, indexes, and sequence state.
3. Compare the trusted applied SQL to the historical candidate files and record any differences explicitly.
4. Build a replay harness only from a reviewed schema-only baseline and verified forward migrations. Do not replay `0001_initial_schema.sql` or data-normalizing legacy files against production.
5. Run the replay and catalog comparison in a disposable PostgreSQL/Supabase-compatible database. Keep production read-only until a separate deployment plan is approved.

## Current conclusion

The history recovers substantial implementation evidence for guest creation, guest-code generation, and the daily litres KPI, plus important permission changes. It does **not** yet establish a complete, exact source-to-ledger mapping for all seven production versions. The next engineering task is to obtain trusted applied SQL and produce a machine-checkable catalog comparison; inventing timestamped migration files or treating filename similarity as proof would be unsafe.
