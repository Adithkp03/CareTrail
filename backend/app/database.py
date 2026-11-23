from sqlalchemy import create_engine, inspect
from sqlalchemy.orm import declarative_base, sessionmaker

from .config import DATABASE_URL

if DATABASE_URL.startswith("sqlite"):
    connect_args = {"check_same_thread": False}
elif DATABASE_URL.startswith("postgresql+psycopg"):
    # Supabase transaction pooler (port 6543) shares server connections across
    # clients, so psycopg's default prepared statements collide there - disable them.
    connect_args = {"prepare_threshold": None}
else:
    connect_args = {}
engine = create_engine(DATABASE_URL, connect_args=connect_args, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def ensure_tables():
    """Create missing tables without one remote existence query per existing table.

    This is only the table-creation check. Privacy and column migrations still
    run on every process startup, unchanged. Missing tables retain SQLAlchemy's
    normal checkfirst behavior for concurrent application starts.
    """
    existing = set(inspect(engine).get_table_names())
    missing = [table for table in Base.metadata.sorted_tables if table.name not in existing]
    if missing:
        Base.metadata.create_all(engine, tables=missing)
