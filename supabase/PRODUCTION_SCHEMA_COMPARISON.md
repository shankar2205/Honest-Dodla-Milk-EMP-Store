# Production schema comparison (read-only)

Date initially checked: 2026-10-23
Read-only recheck: 2026-10-26
Project: `bbnvqktefmdpgzlqlzml` (ap-south-1)

This is a read-only comparison of the live catalog against the legacy migration SQL currently in the repository. No production schema, data, grants, or migration records were changed.

## Verified matches

- Core tables `profiles`, `employees`, `products`, `product_variants`, and `transactions` and their core columns exist.
- `employees.employee_code` is `text`, consistent with `0007_employee_code_text_and_validation.sql`.
- The employee identifier check enforces EMPLOYEE rows with employee_code and GUEST rows with guest_code.
- `public.next_guest_code()` is configured with an empty search_path and is not SECURITY DEFINER in the live catalog. This differs from the early `0002_guest_id_function.sql` implementation, and is closer to the later sequence-based hardening.
- `public.create_employee(text,text,text,text)` and `public.create_guest_employee(text,text,text)` are SECURITY DEFINER with `search_path=public` in production; branch migration `20261023020000_harden_employee_rpc_search_path.sql` proposes hardening these to an empty search_path while schema-qualifying references.
- `private.current_user_role()` is SECURITY DEFINER with empty search_path, matching the hardened helper pattern.
- `public.operator_daily_sales(timestamptz,timestamptz)` exists as SECURITY DEFINER with empty search_path.
- Production migration history is recorded under seven timestamped versions from `20261007100518` through `20261012112124`; it does not show the repository's legacy `0001`–`0009` versions as applied.

## Important unresolved differences / cautions

- The live employee table's catalog constraints returned did **not** include `employees_employee_code_six_digits_check`. The repository's `0007_employee_code_text_and_validation.sql` adds that check as `NOT VALID`; the live absence means we cannot assume all effects of the numbered files are already present.
- Production contains 141 transactions and existing employee/product/profile data; replaying `0001_initial_schema.sql` as a migration is not a safe production operation.
- The initial schema file defines a `public.current_user_role()` helper and original RLS policies; later numbered migrations replace the helper with `private.current_user_role()` and alter policies. A clean rebuild must apply those in dependency order.
- The guest-code history contains materially different implementations: the early `GUEST###` generator, the sequence-backed `GUEST-####` generator, a sequence sync, and a concurrency-safe hardening migration. They must not be collapsed into one renamed file without proving the final function and sequence behavior.
- The two `0007` files do different work: one changes type/adds a check; the other normalizes existing employee identifiers and defines `create_employee`. Ordering and data assumptions matter.
- This catalog fingerprint is partial: it does not yet compare every policy, trigger, index, function body, grant, view definition, or the exact source of each timestamped production migration.

## Safe next steps

1. Fetch the exact SQL for all remaining legacy files (`0004`–`0006`, `0008`, `0009`) and all seven timestamped production migrations from repository history where available.
2. Compare function definitions, grants, RLS predicates, triggers, constraints, views, and indexes against the live catalog.
3. Build a disposable PostgreSQL/Supabase-compatible replay harness from the verified schema and test all migrations in intended order.
4. Produce a new clean local baseline only after the comparison is complete. Keep the production migration ledger and live schema unchanged until separately reviewed.

**Conclusion:** the comparison supports a staged local baseline effort, not a blind rename of the duplicate prefixes. No migration files were renamed or deleted by this step.


## Read-only recheck (2026-10-26)

The connected project catalog and migration ledger were queried again without executing any DDL or data-changing statements.

### Migration ledger observed

The live migration ledger still contains these seven versions:

| Version | Name |
|---|---|
| `20261007100518` | `add_guest_employee_creator` |
| `20261007100537` | `fix_guest_employee_creator_role` |
| `20261007100634` | `restrict_guest_employee_creator_rpc` |
| `20261007102015` | `add_counter_employee_registration` |
| `20261009101008` | `harden_guest_code_generation` |
| `20261012111425` | `operator_daily_litres_kpi` |
| `20261012112124` | `fix_operator_daily_litres_kpi_role_check` |

No `20261023*` branch migration appears in this ledger. The branch's transaction RPC, RLS tuning, and employee RPC search-path hardening migrations remain unapplied to production.

### Core table fingerprint observed

The live `public` schema listing confirms the five core tables and RLS enabled on each:

- `profiles`: id, display_name, username, role, active, created_at, updated_at.
- `employees`: id, employee_code, guest_code, id_type, name, department, phone, active, created_at, updated_at.
- `products`: id, name, active, created_at, updated_at.
- `product_variants`: id, product_id, variant_name, unit_volume_ml, price, active, created_at, updated_at.
- `transactions`: id, employee_id, product_variant_id, quantity, unit_price, generated total_amount, operator_id, transaction_at, created_at.

The catalog listing also confirmed foreign keys linking profiles to auth users and transactions to employees, product variants, and operator profiles. It reports existing data (including 141 transaction rows), further reinforcing that initial-schema replay is not an appropriate production migration strategy.

### Scope and remaining gap

This recheck strengthens the migration-ledger and core-table fingerprint only. It does **not** compare the complete production function bodies, all RLS policies, grants, triggers, views, indexes, or historical migration source. It therefore does not establish that the proposed branch migrations can safely be applied. The next safe engineering step is to add automated catalog assertions and migration tests in the disposable CI database for the exact behavior being changed, while keeping production read-only.


## Read-only security catalog detail (2026-10-26)

A targeted catalog query captured the current production policy predicates and selected RPC security metadata. This is a snapshot, not a security certification.

### Observed RLS policies

- `profiles`: the self-read policy is `id = auth.uid() AND active = true`; an admin management policy also exists.
- `employees`, `products`, and `product_variants`: active-row read policies coexist with admin management policies.
- `transactions`: reads require the current role to be ADMIN or OPERATOR; inserts require `operator_id = auth.uid()` and one of those roles.
- These observed production predicates refer to `auth.uid()` directly in the catalog output, whereas the branch's proposed RLS tuning wraps the UID lookup in a scalar subquery for initplan behavior. Do not assume that tuning migration has been applied.

### Selected function security and grants

| Function | SECURITY DEFINER | search_path | anon EXECUTE | authenticated EXECUTE |
|---|---:|---|---:|---:|
| `private.current_user_role()` | yes | empty | no | yes |
| `public.create_employee(...)` | yes | `public` | no | yes |
| `public.create_guest_employee(...)` | yes | `public` | no | yes |
| `public.next_guest_code()` | no | empty | yes | yes |
| `public.operator_daily_sales(...)` | yes | empty | no | yes |

The production `create_employee` and `create_guest_employee` search paths are a concrete difference addressed by the branch's employee-RPC hardening migration. `next_guest_code()` is executable by anon according to the current privilege check; its body and intended exposure should be reviewed before any change is proposed. This note does not change its grants or behavior.

### Explicitly not concluded

This targeted read did not retrieve every function body, every object grant, trigger definition, index usage statistic, or all migration source SQL. The correct next step is a reviewed diff between the captured production catalog and a clean disposable baseline, followed by focused tests. It is not approval to apply migrations to production.

## Read-only reconciliation update (2026-10-27)

A further catalog comparison was run against project `bbnvqktefmdpgzlqlzml`. It performed catalog/data SELECTs only; no production DDL, grant, data, or migration-ledger changes were made.

### Newly confirmed differences

- `transactions.total_amount` is a stored generated column defined as `quantity * unit_price`. The production `set_transaction_price()` trigger also assigns `new.total_amount`, which is incompatible with writing to a generated column. Branch migration `20261027100000_fix_generated_transaction_total_trigger.sql` removes that assignment and leaves total calculation to PostgreSQL.
- Production has 141 transaction rows at the time of the query. A generated-total test was added to the disposable database workflow so the catalog behavior is exercised alongside the transaction batch tests.
- Production has eight employee records; four employee codes do not match the six-digit format. The mismatches include legacy prefixed and seven-digit identifiers. No identifier values were changed. The proposed employee-code constraint migration was intentionally deferred because existing rows and future updates need a deliberate remediation strategy first.
- Production's `private.current_user_role()` is SECURITY DEFINER with an empty search path and returns a role only for the active profile matching `auth.uid()`. This supports the role checks in the proposed employee RPCs.
- Production's `next_guest_code()` uses an advisory transaction lock, reconciles the sequence against existing `GUEST-####` codes, and returns a zero-padded `GUEST-` identifier. It is currently executable by anon and authenticated roles; the branch migration proposes making it an internal helper only.
- The live catalog has active-row read policies plus admin management policies for employees/products/variants, and role-scoped transaction read/insert policies. The branch's RLS initplan migration changes only the two policies containing direct `auth.uid()` calls; this should be reviewed as a performance-only change.
- The existing `transaction_report` view joins transactions to employees, variants, products, and profiles and derives volume in millilitres/litres. It must be retained in any clean baseline.

### Deployment and migration-history blocker

The live ledger contains seven timestamped migrations and no branch migration versions. The legacy files use duplicate numeric prefixes and some normalize existing employee identifiers. The current branch migrations are not a self-contained production baseline, and a direct `supabase db push` remains unsafe.

Before any production rollout:

1. Obtain the exact SQL for all seven timestamped production migrations from repository history or a trusted export; compare them against live function bodies, grants, triggers, policies, constraints, views, and indexes.
2. Produce a clean schema-only baseline from a verified snapshot, without replaying `0001_initial_schema.sql` or data-normalizing legacy migrations over live data.
3. Apply the baseline and forward migrations in a disposable Supabase-compatible database; include generated-column behavior, role/permission tests, and guest-ID collision/sequence tests.
4. Decide how to handle the four legacy employee-code mismatches. Do not add or validate the six-digit constraint until the chosen policy for those records and their future updates is tested.
5. Review the final migration plan and rollback/verification steps separately. Production remains read-only until explicit deployment approval.

**Current disposition:** reconciliation has found actionable issues and the branch contains a targeted trigger fix, but this is not yet a production deployment approval or a claim of complete schema equivalence.

## Historical migration-source recovery (2026-10-27)

The Git history was traced by migration-path history and the commits that introduced the relevant backend functions. This separates SQL that is directly recoverable from a historical patch from SQL that is merely inferred from the live catalog.

### Source verified in historical commits

- **Guest creation RPC:** the original implementation and authenticated-only grant are visible in [`bfcefa3`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/bfcefa30e6439875453a593be1357a3c5d59523b). It calls the role helper, validates the guest name, allocates a guest code, and inserts a GUEST employee. The later [`481e2a2`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/481e2a22761805a27fc8848de7cc42b27b64a48f) explicitly revokes anon/public execution and grants authenticated execution. The historical implementation uses `search_path = public`; it is not equivalent to the hardened branch proposal.
- **Employee creation RPC grants:** [`832020f`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/832020f6ed6841f5ef7a4098448039d752d60091) records the explicit authenticated-only grant for `create_employee(text,text,text,text)`.
- **Guest-code collision/concurrency hardening:** [`a0dfc78`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/a0dfc78031e04ebff382c733edffcb7e924f7c2b) contains the complete sequence-reconciliation and advisory-lock implementation for `next_guest_code()`. It is strong evidence for the historical function body, but does not by itself prove that the production migration ledger's timestamped migration used byte-identical SQL.
- **Guest duplicate-phone lookup:** [`d37772b`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/d37772b98fe411bbe1af7af286852e76b8fa3093) contains the initial function body and grants. Follow-up commits [`5fe4582`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/5fe4582e54af2d4a8e50a7b1922a6b51c4b9d1dd), [`85b4a4a`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/85b4a4a56864cd5cc38048acf7704f44f0b698f7), and [`09e80b4`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/09e80b40000946e471937f3e0a69b7b761f3ae70) adjust its response behavior and role-helper reference.
- **Daily sales/litres KPI:** [`7497cec`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/7497cecbff1dddf89beffd4bf23285403acba8c9) contains the full `operator_daily_sales(timestamptz,timestamptz)` implementation; [`900785c`](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/900785c5bb0b5c86aaa820963aee4879b2303bee) later tightens its grants to exclude anon.

### What is still not proven

The seven live ledger entries are timestamped versions, while the repository's legacy SQL uses duplicate numeric prefixes. Historical patches recover important function bodies and grant changes, but they do not yet prove an exact one-to-one mapping from each of the seven live version identifiers to the SQL applied in production. In particular, the precise intermediate role-helper correction and the exact applied contents of every timestamped migration remain unverified. Do not create timestamped migration files by guessing from the current catalog or by copying a nearby legacy file and assuming equivalence.

### Next verification gate

1. Compare each recovered historical function/grant patch with the current live `pg_get_functiondef`, `proconfig`, and effective ACL output.
2. Reconstruct a candidate ordered baseline in a **disposable** database and run the existing backend migration workflow from empty state.
3. Compare the disposable catalog fingerprint with production for tables/columns, generated expressions, constraints, policies, triggers, indexes, views, functions, and grants.
4. Only label a migration as source-verified when its contents are tied to the actual applied SQL or a trusted export. Keep inferred steps explicitly marked as inferred.

No production schema, data, grants, or migration-ledger records were changed during this recovery.

## Backend CI hardening follow-up (2026-10-27)

The disposable PostgreSQL workflow was strengthened in commit [69f2e91](https://github.com/shankar2205/Honest-Dodla-Milk-EMP-Store/commit/69f2e9174ea1b8852f1b012f6a25091c1f370cc0):

- Replaced the test-only `next_guest_code()` stub with the repository's actual historical collision-safe generator from `0003_harden_guest_code_generation.sql`.
- Added a seeded legacy guest identifier (`GUEST-0100`) while the sequence starts behind it.
- Tightened the guest-creation assertion to require the next generated identifier to be `GUEST-0101`, exercising sequence reconciliation rather than merely checking that a non-empty code was returned.
- Backend Database Tests run #77 passed all steps, including the real generator migration, guest RPC/duplicate-phone tests, generated transaction total checks, atomic rollback checks, and catalog assertions.

This is a meaningful improvement in behavior coverage, but it is **not yet a complete migration replay**. The workflow still intentionally bootstraps a Supabase-compatible database and applies selected legacy migrations in isolation; it does not prove that every legacy file and all seven timestamped production migrations can be replayed in order to produce a catalog identical to production. Concurrent guest-registration behavior is also not yet tested with parallel sessions.

### Next baseline task

Create an explicit, reviewed source-to-ledger mapping for the seven timestamped production migrations, then build a disposable replay harness from a trusted schema baseline. Do not treat the existing isolated CI bootstrap as proof of full-history replay, and do not apply any inferred baseline to production.

## Legacy employee-code handling decision (2026-11-21)

The four existing production employee rows whose codes do not match the six-digit format are being treated as legacy/test exceptions for this reconciliation. Their employee records, code values, and linked transactions must remain unchanged during the current migration work.

The intended compatibility posture is:

- Do not run 0007_enforce_six_digit_employee_codes.sql against production as part of deployment; it contains data-normalizing UPDATE statements for EMP-prefixed codes.
- Keep employees_employee_code_six_digits_check as NOT VALID in any candidate baseline until legacy rows are deliberately reconciled. A NOT VALID check does not scan/reject existing violations at creation time, but it is enforced on subsequent inserts and updates. Consequently, updates to a legacy row that still violates the check may fail; do not modify those rows as part of this rollout.
- Enforce six-digit values in the employee-creation RPC for all new employee records.
- Do not validate the constraint or rewrite/null/delete the legacy codes without a separately reviewed decision.

The disposable migration assertion now verifies that the six-digit check exists but remains unvalidated, permits NULL at the check-expression level, and that create_employee retains explicit six-digit validation. This is a migration-compatibility assertion, not permission to change production.

No production data or schema was changed for this decision.

## Application grants and direct-write reconciliation (2026-11-21)

A read-only catalog query confirmed that the six application objects (employees, products, product_variants, profiles, transactions, transaction_report) and guest_code_seq are owned by postgres. The five base tables have RLS enabled. The transaction_report view has security_invoker=true; authenticated has SELECT on it and anon does not.

The public-schema default ACLs for objects created by postgres grant all table privileges to anon, authenticated, postgres, and service_role; sequence privileges to those roles; and function EXECUTE to those roles. Similar defaults exist for supabase_admin. The application tables and guest_code_seq are postgres-owned, so the candidate migration scopes application default-privilege changes to postgres and deliberately leaves supabase_admin defaults and service_role access unchanged.

The UI audit found three remaining direct writes:

- Employee details edit: UPDATE public.employees.
- Employee activation/deactivation: UPDATE public.employees.
- Product variant activation/deactivation: UPDATE public.product_variants.

These screens now call guarded SECURITY DEFINER RPCs: update_employee_details, set_employee_active, and set_product_variant_active. The candidate migration then removes anon/authenticated direct table privileges on the six application objects, restores authenticated SELECT, and retains only authenticated INSERT on transactions because create_transaction_batch is SECURITY INVOKER. It also revokes direct client sequence access and tightens postgres-owned future-object defaults.

The disposable backend workflow applies this candidate migration and asserts RPC security, direct-write denial, authenticated read/transaction-insert needs, and future default ACLs. This candidate has passed Backend Database Tests in CI as of 2026-11-21. It has **not** been applied to production; production grants and default privileges remain unchanged.


## Read-only Supabase advisor snapshot (2026-11-21)

Supabase Security and Performance Advisors were queried against the live project. This was an advisory/read-only check; no production settings, schema, grants, or data were changed.

### Security findings

- Three existing authenticated-callable SECURITY DEFINER RPCs were flagged: `create_employee`, `create_guest_employee`, and `operator_daily_sales`. These are intentional application RPC entry points, but they require explicit authorization checks in their function bodies and careful EXECUTE grants. The candidate branch migration adds three more admin-only SECURITY DEFINER RPCs; those are guarded by `auth.uid()` plus `private.current_user_role() = 'ADMIN'`, and CI exercises admin success/operator denial.
- Leaked-password protection is disabled in Supabase Auth. This is a project Auth setting, not a SQL migration. Review enabling it in the Supabase Dashboard under Auth password/security settings before the next production release.

### Performance findings

- The live `profiles` self-read and `transactions` insert policies still call `auth.uid()` without the scalar-subquery initplan form. This matches the known production-vs-branch difference; the branch's RLS tuning migration is intended to address these two policies.
- Four indexes were reported unused at the time of the advisor snapshot: `employees_name_idx`, `transactions_date_idx`, `transactions_product_date_idx`, and `transactions_operator_id_idx`. Do not drop them based on this snapshot alone; usage statistics can reset and workload coverage may be incomplete.
- Multiple permissive SELECT policies were reported for employees, products, product_variants, and profiles. The policies are intentionally layered (active-row read plus admin management); any consolidation should preserve the current row visibility and admin access semantics.

### Default-privilege scope check

A read-only catalog query found postgres-owned function default ACL entries in both `public` and `storage`. The initial candidate migration used a global (non-schema-scoped) `ALTER DEFAULT PRIVILEGES FOR ROLE postgres REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC`, which would have affected future postgres-owned functions in `storage` as well as `public`. This was identified as too broad and has now been removed from the branch migration. The revised migration keeps anon/authenticated default-privilege revocations scoped to `public` and explicitly revokes PUBLIC/anon EXECUTE on each privileged RPC it creates before granting intended caller roles. PostgreSQL's built-in PUBLIC EXECUTE default for future functions remains unchanged globally; future application RPC migrations must continue to revoke it explicitly. Supabase-managed schemas are not altered by this candidate change.

**Disposition:** advisor warnings are review items, not evidence of an active exploit or approval to alter production. No production changes were made by this check.


## Fresh production catalog audit (2026-11-21)

A further read-only audit was run directly against project `bbnvqktefmdpgzlqlzml` using catalog SELECTs and Supabase Security, Performance, and Health Advisors. No production DDL, DML, grants, default privileges, Auth settings, or migration-ledger records were changed.

### Migration ledger and application object inventory

The live migration ledger still contains exactly these seven versions, ending at `20261012112124`:

- `20261007100518 add_guest_employee_creator`
- `20261007100537 fix_guest_employee_creator_role`
- `20261007100634 restrict_guest_employee_creator_rpc`
- `20261007102015 add_counter_employee_registration`
- `20261009101008 harden_guest_code_generation`
- `20261012111425 operator_daily_litres_kpi`
- `20261012112124 fix_operator_daily_litres_kpi_role_check`

No PR migration dated 2026-10-23 through 2026-11-21 appears in the production ledger. The live app catalog currently contains five base tables, one view, and fourteen indexes. It has the five expected RLS-enabled base tables.

Production has 20 employee rows, of which four still fail the six-digit employee-code pattern. This is a count only; identifiers and employee data were not changed. The six-digit CHECK constraint is absent. The existing `employees_identifier_check` requires employee-type rows to have a non-NULL employee_code, so the agreed legacy-row exception remains: leave all four records and linked transactions untouched.

### RPC and RLS drift confirmed

The following branch-target RPCs are absent from production at this audit: `create_transaction_batch`, `save_product_variant`, `update_profile_access`, `find_guest_by_phone`, `create_guest_employee_with_code`, `update_employee_details`, `set_employee_active`, and `set_product_variant_active`. Production still has `create_employee`, `create_guest_employee`, `next_guest_code`, and `operator_daily_sales`.

Production `create_employee` and `create_guest_employee` remain SECURITY DEFINER with `search_path=public`; authenticated can execute them and anon cannot. `next_guest_code()` remains executable by both anon and authenticated, despite the branch proposal to restrict it to internal use. The two RLS policies identified earlier still use direct `auth.uid()` calls: the profile self-read policy and transaction insert policy. Their scalar-subquery/initplan tuning has not been applied.

### Effective client grants: important deployment gap

The fresh privilege query confirms that anon and authenticated retain all seven table privileges (DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE) on each of the five application tables: employees, products, product_variants, profiles, and transactions. Authenticated retains all seven privileges on `transaction_report`; anon has no direct table grant on that view. Anon and authenticated also retain USAGE and SELECT on `public.guest_code_seq`.

The postgres-owned default ACLs still grant anon/authenticated broad table, sequence, and function privileges for future objects in `public`. Postgres-owned defaults also exist in `storage`. Supabase-admin-owned defaults are present across managed schemas, including public, storage, graphql, and graphql_public. This confirms that the candidate privilege-tightening migration has not been applied.

**Security significance:** RLS policies do not replace table-level privilege revocation. The current broad table grants mean clients retain direct SQL privileges subject to RLS; the PR's proposed migration would narrow those grants and route admin mutations through guarded RPCs. Do not apply that migration blindly: first complete the deployment review, validate all app flows against the reduced grants, and resolve the global default-privilege statement's cross-schema scope. The migration deliberately does not alter service_role grants or supabase_admin defaults.

### Supabase Advisor snapshot

- Security: three existing authenticated-callable SECURITY DEFINER functions are flagged: `create_employee`, `create_guest_employee`, and `operator_daily_sales`. These need function-body authorization and EXECUTE-grant review; the warnings alone do not establish an exploit. Leaked-password protection is disabled in Auth and requires a separate Dashboard setting review.
- Performance: the same two RLS initplan warnings remain on `profiles` and `transactions`; four indexes are reported unused (`employees_name_idx`, `transactions_date_idx`, `transactions_product_date_idx`, `transactions_operator_id_idx`). Do not drop indexes based only on a point-in-time usage snapshot.
- Performance: multiple permissive SELECT policies remain on employees, products, product_variants, and profiles. Any consolidation must preserve active-row visibility and admin access.
- Health: no advisor findings were returned in this snapshot.

### Deployment disposition

This audit confirms that production is materially behind the PR's candidate backend migrations, especially in RPC availability, client grants/default ACLs, guest-code generator exposure, and RLS policy tuning. It is a production-vs-branch drift inventory, **not** a full object-by-object equivalence comparison against a captured disposable CI fingerprint. The workflow's production comparison job still needs to be run to produce that artifact.

Do not run `supabase db push`, replay the legacy migration files, or apply the candidate grant/RPC migration to production yet. The next engineering gate is to review the exact production diff and migration order, specifically the global postgres function-default ACL change, and then test the reduced-grant application paths in a disposable environment. Keep the four legacy employee rows unchanged. Production remains read-only pending a separately approved deployment plan.


### Follow-up: function default-privilege scope correction (2026-11-21)

The candidate admin catalog migration was revised after review: the global postgres-owned function default-privilege revocation was removed because it could affect Supabase-managed schemas such as `storage`. The CI assertion that required this global change was also removed. Per-function EXECUTE revocations remain in the application migrations for the privileged RPCs they create; the public-schema default ACL restrictions for anon/authenticated tables, sequences, and functions remain schema-scoped. This is a branch-only correction and has not changed production.


## Migration-chain review follow-up (2026-11-21)

A second read-only review compared the pending SQL definitions with the live production catalog and the Admin Web RPC call sites.

### Verified migration dependencies

- `20261023000000_atomic_transaction_backend.sql` creates `create_transaction_batch` as SECURITY INVOKER. The final grant migration must retain authenticated SELECT on employees, profiles, products, and product_variants plus authenticated INSERT on transactions; otherwise the RPC cannot perform its checks and inserts under the caller's privileges/RLS.
- Production `transactions.total_amount` is a stored generated column with expression `quantity * unit_price`. The live `set_transaction_price()` trigger currently assigns both `new.unit_price` and `new.total_amount`. The proposed `20261027100000_fix_generated_transaction_total_trigger.sql` correctly removes the generated-column assignment and leaves total computation to PostgreSQL. This migration is required before relying on the batch transaction RPC.
- The production `operator_daily_sales(timestamptz,timestamptz)` function is SECURITY DEFINER with an empty search path, checks the active profile for the caller, and is executable by authenticated but not anon. The private role helper is also SECURITY DEFINER with an empty search path and is executable by authenticated but not anon.
- The Admin Web pages changed in this PR call the matching RPCs for employee details, employee status, product-variant status, profile access, product/variant writes, and batched transactions. Read paths continue to use authenticated SELECT.
- The candidate privileged RPC migrations explicitly revoke PUBLIC/anon execution before granting authenticated execution. The employee-creation functions are replaced in place, preserving their already-restricted production ACLs; verify effective ACLs after deployment rather than assuming CREATE OR REPLACE resets them.

### Employee-code rollout constraint

The disposable CI bootstrap applies the legacy `0007_employee_code_text_and_validation.sql` and `0007_enforce_six_digit_employee_codes.sql` files. The latter performs data-normalizing UPDATEs; it must not be replayed against production. The former adds a NOT VALID six-digit CHECK. Although NOT VALID skips scanning existing rows, PostgreSQL enforces it on every subsequent INSERT or UPDATE of a row. Consequently, editing or toggling one of the four legacy employee rows can fail even when the employee_code value is not changed.

The production migration ledger does not contain either legacy `0007` migration, and no timestamped forward migration in this PR adds that CHECK. Therefore the CI assertion that expects the CHECK describes the legacy disposable bootstrap, not the actual production forward-migration sequence. Keep this distinction explicit. Given the agreed decision to leave the four legacy records and linked transactions untouched, do not introduce or validate the CHECK in production in this rollout. The `create_employee` RPC's six-digit validation remains the gate for new employee codes.

### Remaining release blockers

1. Exact applied SQL for all seven production ledger versions is still not available; the source map remains partly inferred. Do not claim a complete historical replay or schema parity.
2. The workflow's production-vs-disposable fingerprint job has not been run with its `workflow_dispatch` input enabled. Existing green CI runs validate the disposable harness only.
3. Run the full application smoke test against a disposable database with the final reduced grants, including Admin and Operator reads, transaction creation, product editing, employee editing/status changes, and profile access changes.
4. Review leaked-password protection in Supabase Auth as a separate dashboard security setting; no Auth setting has been changed.
5. Keep production read-only until the migration source map, deployment order, rollback/verification plan, and an explicit deployment approval are complete.

No production schema, data, grants, default privileges, Auth settings, or migration history were changed during this review.


## Trigger-helper EXECUTE privilege follow-up (2026-11-21)

The latest live catalog fingerprint showed that the trigger-only functions `public.set_updated_at()` and `public.set_transaction_price()` still inherited direct EXECUTE access for client roles (and PUBLIC on `set_updated_at`). These are trigger implementation details, not RPC entry points. The grants migration now explicitly revokes EXECUTE from PUBLIC, anon, and authenticated on both functions. The backend catalog assertion checks that these grants are absent. Existing triggers remain attached; PostgreSQL checks EXECUTE permission when a trigger is created, not each time it fires.

This is a branch-only hardening change. Production has not been modified. Verify the new migration/test revision in CI before considering deployment.
