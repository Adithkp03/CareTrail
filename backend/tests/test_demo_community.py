"""Demo account community access (temporary, user request 2026-11-27):
the shared demo account may join and post so app demos can show the room.
Its identity is forced to a clearly labeled nickname; report/block stay blocked.
"""

from app.database import get_db
from app.main import app
from app.models import Patient
from app.security import hash_password, new_token, token_expiry
from app.models import AuthToken

from .conftest import auth

import pytest


@pytest.fixture(autouse=True)
def community_launch_on(monkeypatch):
    monkeypatch.setenv("COMMUNITY_LAUNCH", "1")


def _demo_headers(client):
    """Create a demo patient + token directly in the test database."""
    gen = app.dependency_overrides[get_db]()
    db = next(gen)
    demo = Patient(name="Demo Anjali", phone="+910000000001",
                   password_hash=hash_password("demo-pass"), is_demo=True)
    db.add(demo)
    db.commit()
    tok = AuthToken(token=new_token(), patient_id=demo.id, expires_at=token_expiry())
    db.add(tok)
    db.commit()
    token = tok.token
    gen.close()
    return auth(token)


def test_demo_join_forces_labeled_nickname(client):
    h = _demo_headers(client)
    r = client.post("/community/join", headers=h, json={"nickname": "HackerMom", "consent": True})
    assert r.status_code == 200, r.text
    assert r.json()["nickname"] == "Anjali (demo)"


def test_demo_can_post_and_read(client):
    h = _demo_headers(client)
    client.post("/community/join", headers=h, json={"nickname": "x", "consent": True})
    r = client.post("/community/messages", headers=h, json={"body": "hello from the demo"})
    assert r.status_code == 201, r.text
    assert r.json()["nickname"] == "Anjali (demo)"
    listing = client.get("/community/messages", headers=h)
    assert listing.status_code == 200


def test_demo_cannot_report_or_block(client):
    h = _demo_headers(client)
    client.post("/community/join", headers=h, json={"nickname": "x", "consent": True})
    msg = client.post("/community/messages", headers=h, json={"body": "demo post"}).json()
    r = client.post(f"/community/messages/{msg['id']}/report", headers=h, json={"reason": "spam", "detail": ""})
    assert r.status_code == 403, r.text
    b = client.post("/community/blocks", headers=h, json={"patient_id": "someone"})
    assert b.status_code == 403, b.text
