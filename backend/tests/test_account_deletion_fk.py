"""Regression tests for account deletion of community authors under real FK
enforcement. SQLite only enforces foreign keys with PRAGMA foreign_keys=ON, so
these tests run against an FK-enforcing engine to mirror production Postgres.

Bug: deleting the account of a patient who had posted in Community failed with
ForeignKeyViolation (community_messages_patient_id_fkey) because the messages
were anonymized but still referenced the patient row being deleted.
"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event, text
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_db
from app.main import app

from .conftest import auth, signup


@pytest.fixture()
def fk_client():
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )

    @event.listens_for(engine, "connect")
    def _fk_on(dbapi_conn, _):
        cur = dbapi_conn.cursor()
        cur.execute("PRAGMA foreign_keys=ON")
        cur.close()

    TestingSession = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    Base.metadata.create_all(engine)

    def override_get_db():
        db = TestingSession()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        c.fk_engine = engine
        yield c
    app.dependency_overrides.clear()


def _join(client, phone, nick):
    headers = auth(signup(client, phone=phone)["token"])
    r = client.post("/community/join", headers=headers, json={"nickname": nick, "consent": True})
    assert r.status_code == 200, r.text
    return headers


def _post(client, headers, body="hello room"):
    r = client.post("/community/messages", headers=headers, json={"body": body})
    assert r.status_code == 201, r.text
    return r.json()


@pytest.fixture(autouse=True)
def community_launch_on(monkeypatch):
    monkeypatch.setenv("COMMUNITY_LAUNCH", "1")


def test_author_deletion_succeeds_under_fk_enforcement(fk_client):
    """An author with live messages can delete their account; messages become
    anonymous tombstones detached from the patient row."""
    h_author = _join(fk_client, "9800000001", "AuthorOne")
    h_other = _join(fk_client, "9800000002", "OtherTwo")
    msg = _post(fk_client, h_author, "author message")
    keep = _post(fk_client, h_other, "other message")

    r = fk_client.request("DELETE", "/me", headers=h_author, json={"confirm": "DELETE"})
    assert r.status_code == 200, r.text
    assert fk_client.get("/auth/me", headers=h_author).status_code == 401

    with fk_client.fk_engine.connect() as conn:
        row = conn.execute(
            text("SELECT patient_id, body, nickname, removed FROM community_messages WHERE id = :i"),
            {"i": msg["id"]},
        ).fetchone()
        assert row is not None, "tombstone must remain"
        assert row[0] is None and row[1] == "" and row[2] == "" and row[1] == row[2] == ""
        assert row[3] in (True, 1)
        other_row = conn.execute(
            text("SELECT patient_id, body FROM community_messages WHERE id = :i"),
            {"i": keep["id"]},
        ).fetchone()
        assert other_row is not None and other_row[1] == "other message"


def test_author_deletion_with_open_report_on_message(fk_client):
    """The exact production failure: author's message was reported by someone
    else (report kept for retention) and the author deletes their account."""
    h_author = _join(fk_client, "9800000011", "AuthorA")
    h_reporter = _join(fk_client, "9800000012", "ReporterB")
    msg = _post(fk_client, h_author, "reported message")
    rr = fk_client.post(
        f"/community/messages/{msg['id']}/report",
        headers=h_reporter,
        json={"reason": "spam", "detail": "flag for review"},
    )
    assert rr.status_code == 201, rr.text

    r = fk_client.request("DELETE", "/me", headers=h_author, json={"confirm": "DELETE"})
    assert r.status_code == 200, r.text

    with fk_client.fk_engine.connect() as conn:
        report = conn.execute(
            text("SELECT status, reporter_patient_id FROM community_reports WHERE message_id = :i"),
            {"i": msg["id"]},
        ).fetchone()
        assert report is not None, "reports filed by others are kept for the 30-day retention"
        tomb = conn.execute(
            text("SELECT patient_id FROM community_messages WHERE id = :i"), {"i": msg["id"]}
        ).fetchone()
        assert tomb is not None and tomb[0] is None


def test_reporter_deletion_removes_only_own_data(fk_client):
    """A reporter deleting their account removes the reports they filed and
    their membership, and leaves the author and message untouched."""
    h_author = _join(fk_client, "9800000021", "AuthorC")
    h_reporter = _join(fk_client, "9800000022", "ReporterD")
    msg = _post(fk_client, h_author, "still here")
    fk_client.post(
        f"/community/messages/{msg['id']}/report",
        headers=h_reporter,
        json={"reason": "other", "detail": ""},
    )

    r = fk_client.request("DELETE", "/me", headers=h_reporter, json={"confirm": "DELETE"})
    assert r.status_code == 200, r.text

    with fk_client.fk_engine.connect() as conn:
        assert conn.execute(text("SELECT count(*) FROM community_reports")).fetchone()[0] == 0
        row = conn.execute(
            text("SELECT patient_id, body FROM community_messages WHERE id = :i"), {"i": msg["id"]}
        ).fetchone()
        assert row is not None and row[0] is not None and row[1] == "still here"
        assert conn.execute(
            text("SELECT count(*) FROM community_members")
        ).fetchone()[0] == 1  # only the author remains
