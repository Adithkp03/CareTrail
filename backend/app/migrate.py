"""Add columns that create_all won't add to existing tables (dev SQLite only;
Supabase/Postgres gets real migrations in the hardening phase)."""

from sqlalchemy import inspect, text

from .database import engine

_PATCHES = {
    "patients": {"consent_at": "ALTER TABLE patients ADD COLUMN consent_at DATETIME"},
}


def apply():
    insp = inspect(engine)
    with engine.begin() as conn:
        for table, patches in _PATCHES.items():
            if table not in insp.get_table_names():
                continue
            existing = {c["name"] for c in insp.get_columns(table)}
            for column, ddl in patches.items():
                if column not in existing:
                    conn.execute(text(ddl))
