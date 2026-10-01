"""Idempotent schema and privacy migrations for SQLite and deployed Postgres."""

from pathlib import Path

from sqlalchemy import inspect, text

from .database import engine

_PATCHES = {
    "patients": {
        "consent_at": "ALTER TABLE patients ADD COLUMN consent_at DATETIME",
        "supabase_id": "ALTER TABLE patients ADD COLUMN supabase_id VARCHAR(64)",
        "email": "ALTER TABLE patients ADD COLUMN email VARCHAR(200)",
    },
    "ai_call_traces": {"patient_id": "ALTER TABLE ai_call_traces ADD COLUMN patient_id VARCHAR(32)"},
    "signoffs": {"clinician_id": "ALTER TABLE signoffs ADD COLUMN clinician_id VARCHAR(32)"},
}


def secure_public_tables(conn):
    """Keep server-owned clinical tables off Supabase client-facing Data API."""
    if conn.dialect.name != "postgresql":
        return
    # Plain Postgres deployments may not define Supabase client roles.
    roles = conn.execute(text("SELECT rolname FROM pg_roles WHERE rolname IN ('anon', 'authenticated')")).scalars().all()
    if not roles:
        return
    if set(roles) != {"anon", "authenticated"}:
        raise RuntimeError("Incomplete Supabase client-role configuration; privacy migration stopped")
    conn.execute(text((Path(__file__).parent / "sql" / "private_backend_tables.sql").read_text()))


def apply():
    insp = inspect(engine)
    with engine.begin() as conn:
        secure_public_tables(conn)
        for table, patches in _PATCHES.items():
            if table not in insp.get_table_names():
                continue
            existing = {c["name"] for c in insp.get_columns(table)}
            for column, ddl in patches.items():
                if column not in existing:
                    conn.execute(text(ddl))
        # Older traces have no reliable owner and may contain sensitive health text.
        # Blank both legacy columns on every startup; this is idempotent and also
        # removes pre-fix traces arriving from another stale instance.
        if "ai_call_traces" in insp.get_table_names():
            conn.execute(text("UPDATE ai_call_traces SET prompt = :blank, output = :blank WHERE prompt <> :blank OR output <> :blank"), {"blank": ""})
