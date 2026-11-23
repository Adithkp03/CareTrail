from sqlalchemy import create_engine, event, inspect, text
from app import database
from app.models import Patient


def test_existing_schema_needs_one_catalog_read(monkeypatch):
    engine = create_engine("sqlite://")
    database.Base.metadata.create_all(engine)
    monkeypatch.setattr(database, "engine", engine)
    queries = []
    event.listen(engine, "before_cursor_execute", lambda c, cur, statement, *args: queries.append(statement))
    database.ensure_tables()
    assert len(queries) == 1, queries
    assert "sqlite_master" in queries[0]


def test_bootstrap_creates_missing_tables_without_losing_rows(monkeypatch):
    engine = create_engine("sqlite://")
    Patient.__table__.create(engine)
    with engine.begin() as conn:
        conn.execute(text("INSERT INTO patients (id, name, phone, password_hash, language, is_demo, created_at) VALUES (:id, :name, :phone, :password, :lang, 0, CURRENT_TIMESTAMP)"), {"id": "kept", "name": "Example", "phone": "fixture-only", "password": "not-a-login", "lang": "en"})
    monkeypatch.setattr(database, "engine", engine)
    database.ensure_tables()
    assert set(database.Base.metadata.tables) <= set(inspect(engine).get_table_names())
    # Repeated startup is safe and does not recreate existing tables.
    database.ensure_tables()
    assert "patients" in inspect(engine).get_table_names()
    with engine.connect() as conn:
        assert conn.execute(text("SELECT name FROM patients WHERE id = :id"), {"id": "kept"}).scalar_one() == "Example"


def test_main_still_runs_privacy_migrations_after_schema_check():
    from pathlib import Path
    source = (Path(__file__).parents[1] / "app" / "main.py").read_text()
    assert source.index("ensure_tables()") < source.index("apply_migrations()")
