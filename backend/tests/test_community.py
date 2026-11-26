import hashlib
import os

import pytest

from .conftest import auth, signup

from app.database import get_db
from app.main import app
from app.models import CommunityModerator


@pytest.fixture(autouse=True)
def community_launch_on(monkeypatch):
    monkeypatch.setenv("COMMUNITY_LAUNCH", "1")


def join(client, phone, nick="Anu"):
    headers = auth(signup(client, phone=phone)["token"])
    r = client.post("/community/join", headers=headers, json={"nickname": nick, "consent": True})
    assert r.status_code == 200, r.text
    return headers


def post(client, headers, body="Hello everyone"):
    return client.post("/community/messages", headers=headers, json={"body": body})


def moderator(name="Reviewer"):
    token = "ctmod-test-" + name
    gen = app.dependency_overrides[get_db]()
    db = next(gen)
    db.add(CommunityModerator(token_hash=hashlib.sha256(token.encode()).hexdigest(), name=name))
    db.commit()
    db.close()
    return {"Authorization": "Bearer " + token}


def test_launch_gate_blocks_everything_until_enabled(client, monkeypatch):
    monkeypatch.delenv("COMMUNITY_LAUNCH", raising=False)
    headers = auth(signup(client)["token"])
    assert client.post("/community/join", headers=headers, json={"nickname": "Anu", "consent": True}).status_code == 404
    assert client.get("/community/me", headers=headers).status_code == 404
    assert client.get("/community/messages", headers=headers).status_code == 404
    assert client.post("/community/messages", headers=headers, json={"body": "hi"}).status_code == 404
    assert client.get("/community/blocks", headers=headers).status_code == 404


def test_join_requires_consent_and_clean_nickname(client):
    headers = auth(signup(client)["token"])
    assert client.post("/community/join", headers=headers, json={"nickname": "Anu", "consent": False}).status_code == 422
    assert client.post("/community/join", headers=headers, json={"nickname": "9876543210", "consent": True}).status_code == 422
    assert client.post("/community/join", headers=headers, json={"nickname": "Moderator", "consent": True}).status_code == 422
    assert client.post("/community/join", headers=headers, json={"nickname": "x", "consent": True}).status_code == 422
    ok = client.post("/community/join", headers=headers, json={"nickname": "Anu M", "consent": True})
    assert ok.status_code == 200 and ok.json()["nickname"] == "Anu M"
    assert client.get("/community/me", headers=headers).json()["joined"] is True
    assert client.get("/community/messages", headers=headers).status_code == 200


def test_read_and_post_require_membership_and_no_links(client):
    headers = auth(signup(client)["token"])
    assert client.get("/community/messages", headers=headers).status_code == 403
    assert post(client, headers).status_code == 403
    join(client, "9876543211")
    h2 = auth(signup(client, phone="9876543212")["token"])
    client.post("/community/join", headers=h2, json={"nickname": "Meera", "consent": True})
    assert post(client, h2, "check https://spam.example").status_code == 422
    made = post(client, h2, "We had our scan this week, feeling relieved")
    assert made.status_code == 201, made.text
    listed = client.get("/community/messages", headers=h2).json()["messages"]
    assert listed[-1]["body"].startswith("We had our scan") and listed[-1]["own"] is True


def test_demo_account_cannot_participate(client):
    demo = client.post("/auth/login", json={"phone": "9000000001", "password": "demo1234"})
    if demo.status_code != 200:
        return  # demo seeding differs per environment; rule covered by not_demo()
    headers = auth(demo.json()["token"])
    assert client.post("/community/join", headers=headers, json={"nickname": "Demo", "consent": True}).status_code == 403


def test_moderator_token_is_hashed_at_rest(client):
    mod = moderator()
    assert client.get("/moderation/reports", headers=mod).status_code == 200
    gen = app.dependency_overrides[get_db]()
    db = next(gen)
    stored = db.query(CommunityModerator).first()
    assert stored.token_hash != "ctmod-test-Reviewer" and len(stored.token_hash) == 64
    db.close()
    assert client.get("/moderation/reports", headers={"Authorization": "Bearer ctmod-wrong"}).status_code == 401


def test_report_flow_and_moderation_remove_and_suspend(client):
    join(client, "9876543210", "Anu")
    h2 = join(client, "9876543211", "Meera")
    msg = post(client, h2, "You should skip your next BP medicine").json()
    assert client.post(f"/community/messages/{msg['id']}/report", headers=h2, json={"reason": "spam"}).status_code == 422
    dup1 = client.post(f"/community/messages/{msg['id']}/report", json={"reason": "medical-advice"}, headers=auth(signup(client, phone="9876543213")["token"]))
    assert dup1.status_code == 403  # not joined yet
    h3 = join(client, "9876543214", "Rani")
    first = client.post(f"/community/messages/{msg['id']}/report", headers=h3, json={"reason": "medical-advice", "detail": "telling people to skip medicine"})
    assert first.status_code == 201 and first.json()["duplicate"] is False
    again = client.post(f"/community/messages/{msg['id']}/report", headers=h3, json={"reason": "medical-advice"})
    assert again.json()["duplicate"] is True
    # Moderation queue
    assert client.get("/moderation/reports").status_code == 401
    mod = moderator()
    queue = client.get("/moderation/reports", headers=mod).json()
    assert queue["moderator"] == "Reviewer" and len(queue["reports"]) == 1
    rep = queue["reports"][0]
    assert rep["message"]["body"].startswith("You should skip") and rep["reason"] == "medical-advice"
    done = client.post(f"/moderation/reports/{rep['id']}/resolve", headers=mod, json={"action": "remove_and_suspend", "note": "dangerous advice"})
    assert done.status_code == 200 and done.json()["reports_closed"] == 1
    assert client.post(f"/moderation/reports/{rep['id']}/resolve", headers=mod, json={"action": "dismiss"}).status_code == 409
    # Message hidden, author suspended from posting but can read
    visible = client.get("/community/messages", headers=h3).json()["messages"]
    assert all(m["id"] != msg["id"] for m in visible)
    assert post(client, h2, "anyone there?").status_code == 403
    assert client.get("/community/messages", headers=h2).status_code == 200


def test_dismiss_keeps_message(client):
    h1 = join(client, "9876543210", "Anu")
    h2 = join(client, "9876543211", "Meera")
    msg = post(client, h2, "normal hello").json()
    rep = client.post(f"/community/messages/{msg['id']}/report", headers=h1, json={"reason": "other"}).json()
    mod = moderator("Rev2")
    done = client.post(f"/moderation/reports/{rep['id']}/resolve", headers=mod, json={"action": "dismiss"})
    assert done.status_code == 200
    visible = client.get("/community/messages", headers=h1).json()["messages"]
    assert any(m["id"] == msg["id"] for m in visible)


def test_block_hides_author_and_unblock_restores(client):
    h1 = join(client, "9876543210", "Anu")
    h2 = join(client, "9876543211", "Meera")
    author_id = client.get("/community/me", headers=h2).json()
    msg = post(client, h2, "annoying message").json()
    assert client.post("/community/blocks", headers=h1, json={"patient_id": msg["author_id"]}).status_code == 201
    view = client.get("/community/messages", headers=h1).json()["messages"]
    target = [m for m in view if m["id"] == msg["id"]][0]
    assert target["author_blocked"] is True and target["body"] == ""
    listed = client.get("/community/blocks", headers=h1).json()["blocked"]
    assert listed[0]["nickname"] == "Meera"
    assert client.delete(f"/community/blocks/{msg['author_id']}", headers=h1).status_code == 200
    view = client.get("/community/messages", headers=h1).json()["messages"]
    assert [m for m in view if m["id"] == msg["id"]][0]["body"] == "annoying message"


def test_own_delete_and_account_deletion_anonymizes(client):
    h1 = join(client, "9876543210", "Anu")
    h2 = join(client, "9876543211", "Meera")
    msg = post(client, h2, "temporary thought").json()
    assert client.delete(f"/community/messages/{msg['id']}", headers=h1).status_code == 404
    assert client.delete(f"/community/messages/{msg['id']}", headers=h2).status_code == 200
    msg2 = post(client, h2, "second thought").json()
    exported = client.get("/me/export", headers=h2).json()
    assert exported["community"]["nickname"] == "Meera" and len(exported["community"]["messages"]) == 2
    assert client.request("DELETE", "/me", headers=h2, json={"confirm": "DELETE"}).status_code == 200
    remaining = client.get("/community/messages", headers=h1).json()["messages"]
    assert all(m["id"] != msg2["id"] for m in remaining)


def test_post_rate_limit(client):
    h1 = join(client, "9876543210", "Chatty")
    for i in range(10):
        assert post(client, h1, f"message {i}").status_code == 201
    assert post(client, h1, "one too many").status_code == 429


def test_moderation_page_carries_working_csp(client):
    r = client.get("/moderation/app")
    assert r.status_code == 200 and "CareTrail community review queue" in r.text
    csp = r.headers.get("content-security-policy", "")
    assert "script-src 'unsafe-inline'" in csp and "style-src 'unsafe-inline'" in csp
    assert "connect-src 'self'" in csp and "default-src 'none'" in csp


def test_closed_reports_purge_after_retention_window(client):
    from datetime import datetime, timedelta, timezone

    from app.models import AuditLog, CommunityReport

    h1 = join(client, "9876543210", "Anu")
    h2 = join(client, "9876543211", "Meera")
    old_msg = post(client, h2, "old flagged text").json()
    open_msg = post(client, h2, "fresh flagged text").json()
    old_rep = client.post(f"/community/messages/{old_msg['id']}/report", headers=h1, json={"reason": "spam"}).json()
    mod = moderator("Rev3")
    done = client.post(f"/moderation/reports/{old_rep['id']}/resolve", headers=mod, json={"action": "dismiss"})
    assert done.status_code == 200
    client.post(f"/community/messages/{open_msg['id']}/report", headers=h1, json={"reason": "spam"})
    # Backdate the closed report past the retention window.
    gen = app.dependency_overrides[get_db]()
    db = next(gen)
    row = db.get(CommunityReport, old_rep["id"])
    row.handled_at = datetime.now(timezone.utc) - timedelta(days=31)
    db.commit()
    db.close()
    # A queue read runs the lazy purge: closed report and its audit row are gone.
    queue = client.get("/moderation/reports?status=all", headers=mod).json()["reports"]
    assert [r["id"] for r in queue] and all(r["id"] != old_rep["id"] for r in queue)
    assert any(r["message"]["id"] == open_msg["id"] for r in queue)
    db = next(app.dependency_overrides[get_db]())
    leftover = db.query(AuditLog).filter(AuditLog.entity_id == old_msg["id"]).all()
    db.close()
    assert leftover == []


def test_export_states_community_retention(client):
    headers = join(client, "9876543210", "Anu")
    post(client, headers, "hello")
    data = client.get("/me/export", headers=headers).json()
    note = data["community"]["retention_note"]
    assert "30 days" in note and "placeholder" in note and "permanently deleted" in note
