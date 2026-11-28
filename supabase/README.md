# Supabase setup

## Important: migration history is being reconciled

The SQL files under `supabase/migrations/` include legacy numeric prefixes that are not a reliable, unique chronological ordering. **Do not treat `0001_initial_schema.sql` as a complete setup, and do not run the legacy files one-by-one against production.** Production has a separate timestamped migration history.

Before creating or changing a database:

1. Review [`MIGRATION_HISTORY.md`](./MIGRATION_HISTORY.md) for known legacy migration conflicts and the safe reconciliation plan.
2. Review [`PRODUCTION_SCHEMA_COMPARISON.md`](./PRODUCTION_SCHEMA_COMPARISON.md) for the current read-only comparison and its limitations.
3. Use a disposable local PostgreSQL/Supabase-compatible environment for migration work. The repository's database CI is a partial harness; a green run does not prove that every legacy migration can rebuild a complete Supabase database.
4. Do not run `supabase db push`, apply migrations, or alter production schema/history until a reconciled baseline has been reviewed and explicitly approved.

## Client configuration

Configure the Supabase project URL and anon/publishable key in each client through environment variables. Never commit service-role keys or other secrets. Keep privileged keys server-side only.
