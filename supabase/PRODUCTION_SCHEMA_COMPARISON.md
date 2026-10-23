# Production schema comparison (read-only)

Date checked: 2026-10-23
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
