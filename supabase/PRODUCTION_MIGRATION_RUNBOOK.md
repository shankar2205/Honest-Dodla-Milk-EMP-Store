# Production migration runbook (draft — not approved for execution)

**Status:** Preparation only. This document does not authorize a production change.  
**PR:** #20 — `rebuild/backend-foundation-2026-10-23`  
**Reviewed source head:** `0876836ff41685a252512395b96a6368a2eeb295`  
**Scope:** The ten timestamped forward migrations listed below. This is not a procedure for replaying the legacy `0001`–`0009` files.

## 1. Hard stop / migration-history gate

Production currently has seven timestamped records in `supabase_migrations.schema_migrations`. The repository's ten forward migrations have been replayed successfully on a disposable schema-only production baseline, but that replay did **not** reconcile or update the production ledger.

The legacy numbered files also contain duplicate version prefixes and data-changing historical logic. Therefore:

- Do **not** run `supabase db push`, `supabase migration repair`, or rename/replay the legacy numbered migrations as a shortcut.
- Do **not** execute the ten files in the production SQL Editor until a reviewed deployment mechanism has been chosen that both applies the SQL and leaves migration history accurate.
- Do **not** manually insert rows into `schema_migrations` or mark migrations applied without a separately reviewed, evidence-backed reconciliation.
- If the migration-history recording method cannot be established and tested on a disposable copy, stop. Production remains unchanged.

The current migration 10 is hosted-compatible and leaves platform-managed `supabase_admin` default privileges unchanged. Do not add a `supabase_admin` membership or privilege change as a prerequisite. The runner must have the normal authority required by the reviewed SQL; verify its actual identity and permissions before release.

## 2. Exact forward-migration order

Apply only after Section 1 is resolved, using the exact committed files at the reviewed head. Do not skip, reorder, combine, or substitute files.

| Order | Migration | Purpose / critical behavior |
|---:|---|---|
| 1 | `20261023000000_atomic_transaction_backend.sql` | Adds authenticated `create_transaction_batch`; derives operator and catalog prices server-side; validates active employee/items; inserts the batch atomically. |
| 2 | `20261023010000_rls_auth_uid_initplan_tuning.sql` | Recreates two policies with scalar-subquery `auth.uid()` evaluation; intended to preserve predicates. |
| 3 | `20261023020000_harden_employee_rpc_search_path.sql` | Replaces employee/guest creation RPC bodies with empty search paths. New EMPLOYEE codes must be six digits; this changes RPC validation, not existing employee rows. |
| 4 | `20261026000000_restrict_guest_code_generator.sql` | Revokes direct client execution of `next_guest_code()` and direct client access to `guest_code_seq`. |
| 5 | `20261027000000_restore_admin_guest_employee_rpcs.sql` | Adds authenticated `find_guest_by_phone` and ADMIN-only `create_guest_employee_with_code`. |
| 6 | `20261027100000_fix_generated_transaction_total_trigger.sql` | Replaces the transaction price trigger function and recreates its BEFORE INSERT trigger. |
| 7 | `20261027120000_atomic_product_variant_admin_write.sql` | Adds guarded `save_product_variant` RPC for atomic product/variant writes. |
| 8 | `20261027130000_guard_profile_access_changes.sql` | Adds guarded `update_profile_access` RPC and revokes direct authenticated UPDATE on profiles. |
| 9 | `20261121000000_secure_admin_catalog_writes_and_grants.sql` | Adds guarded admin RPCs, resets table grants, revokes client sequence access, and hardens postgres-owned public-schema default privileges for anon/authenticated. |
| 10 | `20261123000000_reconcile_policy_role_helpers_and_admin_defaults.sql` | Recreates six policies against `private.current_user_role()`, and reconciles helper grants. Leaves `supabase_admin` defaults unchanged. |

Migration 9 intentionally preserves `service_role` defaults. Migration 10 must be the current committed revision; do not use an older copy that attempted to change `supabase_admin` defaults.

## 3. Mandatory preflight (read-only)

Complete and attach evidence for every item before scheduling execution:

1. Confirm the target project/ref and maintenance window with the release owner.
2. Record `current_user`, `session_user`, server version, and the exact migration-runner identity. Confirm the runner can execute each reviewed statement, including `ALTER DEFAULT PRIVILEGES FOR ROLE postgres`. Do not assume dashboard or CLI identity.
3. Export the current production migration ledger read-only. Reconcile the seven recorded versions with the current live catalog and document how the ten new versions will be recorded. No ledger writes during preflight.
4. Take a fresh full logical backup and a fresh schema-only snapshot. Verify checksums and perform a restore test into a disposable database before approval. The November 27/28 artifacts are historical validation evidence, not a substitute for a fresh release backup.
5. Capture a before-state catalog evidence bundle: function definitions, owners, SECURITY DEFINER flags, `proconfig/search_path`, effective EXECUTE privileges, table/sequence grants, `pg_default_acl`, RLS enabled flags and policies, triggers, views, constraints, indexes, and sequence state.
6. Compare the live catalog with the reviewed schema baseline and identify drift. Resolve differences before proceeding; do not assume the schema-only restore preserved production ACLs (it was made with `--no-acl`).
7. Confirm the deployed frontend build is the matching PR revision and that all four required CI workflows pass on the exact final commit.
8. Record before-state invariants: employee rows and employee codes, linked transaction counts, product/variant counts, profile/admin counts, transaction counts, and guest-code sequence state. Explicitly protect the four legacy employee records and their 69 linked transactions observed in the validated full restore; re-confirm live values rather than treating those historical counts as current.
9. Review the rollback package in Section 5 and obtain explicit written approval for the exact commit, migration list, runner, and execution window.

**No-go conditions:** unexplained catalog drift; unverified backup/restore; uncertain migration-ledger handling; insufficient runner privileges; failed CI; unexpected legacy employee/transaction differences; or missing release approval.

## 4. Execution and verification protocol

Once all preflight gates are signed off:

1. Freeze unrelated schema changes and record the exact starting commit, ledger rows, catalog evidence, and backup identifiers.
2. Apply the exact migration files in Section 2 in order through the approved runner. Execute one complete migration at a time. Capture the runner output and resulting migration-history record after each file.
3. After each migration, run its targeted catalog assertions and a small non-destructive smoke check. If any statement errors, the resulting catalog differs from expectation, or migration history is ambiguous: **stop immediately**. Do not continue, rerun blindly, or improvise SQL.
4. After migration 10, run the complete post-deployment verification below before reopening normal traffic.

### Post-deployment verification

- All five application tables retain RLS enabled and the expected two policies each; all six Admin/Operator policy predicates use `private.current_user_role()`.
- The expected eight RPCs exist with empty `search_path`; SECURITY DEFINER settings and owners match the reviewed inventory.
- `create_transaction_batch`, `save_product_variant`, `update_profile_access`, `create_guest_employee_with_code`, and `find_guest_by_phone` have only their intended authenticated EXECUTE grants; `next_guest_code` is not directly executable by client roles.
- Table and sequence grants match the reviewed matrix. Authenticated users retain the intended reads and transaction insert path; direct profile UPDATE is revoked.
- Postgres-owned public-schema default privileges no longer grant tables, sequences, or function EXECUTE to anon/authenticated. `service_role` defaults are unchanged. `supabase_admin` defaults are unchanged.
- The transaction price trigger exists and sets catalog price; generated `total_amount` remains generated by PostgreSQL.
- Application smoke tests pass for ADMIN and OPERATOR flows, inactive/unauthorized callers, employee creation, guest lookup/creation, transaction batches, product/variant writes, and profile access changes. Confirm the last-active-admin safeguard.
- Recheck all preflight data invariants. No legacy employee code, employee row, or linked transaction may be normalized, deleted, or rewritten by this migration chain.
- Confirm the production migration ledger records exactly the reviewed versions using the approved mechanism. Archive all SQL output, assertions, and before/after evidence.

## 5. Rollback and recovery boundaries

There are no down-migration files in this chain. Do not invent reverse SQL during an incident.

- Before execution, prepare and peer-review exact compensating SQL from the captured live definitions, grants, policies, trigger state, and default ACLs. This must restore the actual before-state, not a guessed state.
- If a migration fails, stop at that migration. Preserve logs and inspect the committed/rolled-back state before deciding whether to resume or restore.
- If post-deployment verification fails, disable affected application paths or roll back the application release as appropriate. Do not delete or rewrite application data to make checks pass.
- Reversing migration 9 requires restoring the captured postgres-owned default ACL state as well as table/sequence/function grants. Reversing migration 10 requires restoring the exact prior policy definitions and helper ACLs. Never grant broad PUBLIC/anon access as a generic rollback.
- Reversing migration 6 requires restoring the exact prior trigger and function definition. Reversing migration 8 requires restoring the prior profile UPDATE grant and function state only after reviewing the security consequences.
- Use the verified backup/PITR recovery plan only under incident command approval. A database restore can overwrite writes made after the restore point; it is not a casual rollback.
- After any recovery, verify data invariants, catalog state, application behavior, and migration-ledger consistency before reopening traffic.

## 6. Approval record (must be completed before production execution)

- Reviewed commit SHA:
- Production project/ref:
- Approved migration-ledger recording mechanism:
- Backup artifact and verified restore evidence:
- Runner identity and privilege evidence:
- Preflight reviewer(s):
- Rollback SQL reviewer(s):
- Execution owner and window:
- Explicit production-change approval:
- Final go/no-go decision:

**Current decision: NO-GO / NOT AUTHORIZED.** The migration chain has disposable-database validation, but production migration-history reconciliation and release approval remain outstanding. No production schema, data, grants, default privileges, migration history, or Auth settings have been changed by this runbook.
