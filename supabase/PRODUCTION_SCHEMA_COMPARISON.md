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
