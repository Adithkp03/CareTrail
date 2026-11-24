from datetime import date, timedelta
from uuid import uuid4

from .conftest import signup, auth


def journey(client, phone="9876543210"):
    headers = auth(signup(client, phone=phone)["token"])
    r = client.post("/journeys", headers=headers, json={"lmp": (date.today()-timedelta(days=140)).isoformat()})
    assert r.status_code == 201
    return headers, r.json()["journey_id"]


def write(client, headers, jid, module="birth", rev=0, op=None, data=None, deleted=False):
    return client.put(f"/journeys/{jid}/local-backup/{module}", headers=headers,
                      json={"consent": True, "expected_revision": rev, "operation_id": op or str(uuid4()),
                            "schema_version": 1, "deleted": deleted, "data": data if data is not None else {"confirmed": True}})


def test_opt_in_owned_and_no_automatic_population(client):
    h, jid = journey(client)
    assert client.get(f"/journeys/{jid}/local-backup", headers=h).json()["records"] == []
    assert client.get(f"/journeys/{jid}/local-backup").status_code == 401
    other, _ = journey(client, "9876543211")
    assert client.get(f"/journeys/{jid}/local-backup", headers=other).status_code == 404
    assert write(client, other, jid).status_code == 404
    assert write(client, h, jid, module="token").status_code == 422
    assert client.put(f"/journeys/{jid}/local-backup/birth", headers=h,
                      json={"expected_revision": 0, "operation_id": str(uuid4()), "data": {}}).status_code == 422


def test_idempotency_conflict_tombstone_and_restore_read(client):
    h, jid = journey(client)
    op = str(uuid4())
    a = write(client, h, jid, op=op)
    assert a.status_code == 200 and a.json()["revision"] == 1
    assert a.headers["cache-control"] == "no-store"
    assert write(client, h, jid, op=op).json()["revision"] == 1
    assert write(client, h, jid, op=op, data={"confirmed": False}).status_code == 409
    assert write(client, h, jid, rev=0).status_code == 409
    assert write(client, h, jid, rev=1, data={"confirmed": False}).json()["revision"] == 2
    deleted = write(client, h, jid, rev=2, deleted=True)
    assert deleted.status_code == 200 and deleted.json()["data"] is None
    assert write(client, h, jid, rev=1).status_code == 409
    rows = client.get(f"/journeys/{jid}/local-backup", headers=h).json()["records"]
    assert rows[0]["deleted"] is True and rows[0]["revision"] == 3


def test_size_schema_export_and_account_deletion(client):
    h, jid = journey(client)
    assert write(client, h, jid, data={"x": "x"*65537}).status_code == 413
    assert write(client, h, jid, data="scalar").status_code == 422
    assert write(client, h, jid, module="mood", data=[{"score": 2}]).status_code == 200
    data = client.get("/me/export", headers=h).json()
    assert data["journeys"][0]["local_record_backup"][0]["module"] == "mood"
    assert client.request("DELETE", "/me", headers=h, json={"confirm": "DELETE"}).status_code == 200
    assert client.get(f"/journeys/{jid}/local-backup", headers=h).status_code == 401


def test_demo_cannot_upload_and_invalid_schema_is_rejected(client):
    from app.database import get_db
    from app.main import app
    from app.models import Patient
    h, jid = journey(client)
    assert client.put(f"/journeys/{jid}/local-backup/birth", headers=h,
                      json={"consent": True, "expected_revision": 0, "operation_id": str(uuid4()),
                            "schema_version": 2, "data": {}}).status_code == 422
    gen = app.dependency_overrides[get_db]()
    db = next(gen)
    p = db.query(Patient).first()
    p.is_demo = True
    db.commit()
    gen.close()
    assert write(client, h, jid).status_code == 403


def test_credentials_and_local_file_references_rejected(client):
    h, jid = journey(client)
    assert write(client, h, jid, module="registration", data={"mcp_images": ["/private/file"]}).status_code == 422
    assert write(client, h, jid, data={"nested": {"access_token": "not-a-real-token"}}).status_code == 422
