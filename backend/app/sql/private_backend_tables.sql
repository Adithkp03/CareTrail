-- CareTrail owns this Supabase project's public tables. They are accessed only
-- through FastAPI's direct server connection, not the anon/authenticated Data API.
-- RLS has no client policies: deny client access; leave postgres/server role alone.
-- Run as postgres/table owner. This transaction must fail rather than silently skip.
DO $caretrail$
DECLARE table_row record; owner_row record;
BEGIN
    FOR table_row IN
        SELECT tablename FROM pg_tables WHERE schemaname = 'public'
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_row.tablename);
        EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM anon, authenticated', table_row.tablename);
    END LOOP;
    REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
    -- Default grants are per creating role. Protect every current public-table
    -- owner and the executing role, including future backend-created tables.
    FOR owner_row IN
        SELECT DISTINCT tableowner AS name FROM pg_tables WHERE schemaname = 'public'
        UNION SELECT current_user AS name
    LOOP
        EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated', owner_row.name);
        EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated', owner_row.name);
    END LOOP;
END
$caretrail$;
