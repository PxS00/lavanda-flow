-- Provider-only workaround for disabled Data API; not an application migration.
-- Preconditions: Data API disabled; schema absent; no authenticator db_schemas override.
-- https://supabase.com/docs/guides/troubleshooting/schema-pg_pgrst_no_exposed_schemas-does-not-exist
BEGIN;
CREATE SCHEMA pgrst_no_exposed_schemas;
ALTER ROLE authenticator SET pgrst.db_schemas = 'pgrst_no_exposed_schemas';
NOTIFY pgrst;
COMMIT;
