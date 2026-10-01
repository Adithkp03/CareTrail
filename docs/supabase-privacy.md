# Supabase client Data API boundary

CareTrail's clinical data is owned by FastAPI. It connects to Postgres directly,
verifies its bearer tokens against `auth_tokens`, and checks patient/clinician
ownership. The frontend does not query public tables through Supabase REST.
Supabase login, when configured, is exchanged for a CareTrail session.

The October 1 dashboard inspection found all public tables without RLS and broad
anon/authenticated table grants. Possession of the public-by-design anon key
would bypass FastAPI ownership checks. No such key was found in deployed bundles;
that reduces immediate discovery, but is not an authorization boundary.

## Fix

The startup migration enables RLS on every public table and revokes all table
and sequence privileges from Supabase's `anon` and `authenticated` client roles.
It creates no client policies. Existing server/postgres credentials and grants
are unchanged; RLS is not forced against table owners. Default table/sequence
privileges are revoked for all existing public-table owners and the migration
role, preventing newly created backend tables inheriting client access. Both
global and public-schema default grants are revoked because a schema-level
revoke cannot cancel a global grant. This also stops auto-grants on future objects
created by these same roles in other schemas; no server privileges are changed.

This project must contain only CareTrail-owned tables in its public schema.
A dedicated non-owner server role must have its intended BYPASSRLS/owner access
validated before deployment; this migration intentionally fails loudly if the
server cannot enforce this boundary. Do not add an auth.uid() patient policy:
CareTrail IDs and its phone/password tokens are not Supabase auth identities.

## Deploy and verify

1. Review and merge this PR before exposing new clinical tables. Restart backend
   so its existing startup migration runs under the server DB role.
2. Confirm all public-table RLS flags are enabled and anon/authenticated grants
   are absent; verify the default privileges in Supabase SQL metadata.
3. With a public anon key (never print it), request `/rest/v1/patients?select=id&limit=0`
   and repeat for auth_tokens, audit_logs, observations and reminder_states. The
   Data API must deny table access. Use limit=0 to avoid fetching patient rows.
4. Test existing CareTrail login, journey load, upload/confirmation, clinician
   authorization and sign-off via FastAPI. Verify normal server writes succeed.
5. Future schema owners require the same default-privilege rule. New roles and
   SECURITY DEFINER RPC functions must be reviewed separately before use.

Local tests verify branching and SQL scope. The actual PostgreSQL migration and
post-deploy REST denial must be tested against this project's DB; SQLite cannot
prove PostgreSQL privilege behavior. Do not claim it fixed the live site until
that verification succeeds. Failed security migrations should not be ignored.
