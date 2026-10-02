from app.database import get_db
from app.models import AuditLog
from tests.conftest import auth, signup
from tests.test_clinicians import provision


def test_instruction_owner_ack_and_revocation(client):
    token = signup(client)["token"]
    other = signup(client, phone="9000000081")["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token)).json()["journey_id"]
    c = provision(client)
    doc = client.post("/clinician/login", json={"email": c.email, "password": "correct horse 123"}).json()["token"]
    url = f"/clinician/journeys/{j}/instructions"
    assert client.post(url, json={"note": "Please bring your report."}, headers=auth(doc)).status_code == 404
    client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c.email}, headers=auth(token))
    assert client.post(url, json={"note": " "}, headers=auth(doc)).status_code == 422
    assert client.post(url, json={"note": "x" * 4001}, headers=auth(doc)).status_code == 422
    assert client.post(url, json={"note": "test"}, headers=auth(token)).status_code == 401
    note = client.post(url, json={"note": "Please bring your report."}, headers=auth(doc))
    assert note.status_code == 201
    nid = note.json()["id"]
    purl = f"/journey/{j}/instructions"
    assert client.get(purl, headers=auth(other)).status_code == 404
    assert client.post(f"/instructions/{nid}/acknowledge", headers=auth(other)).status_code == 404
    item = client.get(purl, headers=auth(token)).json()["items"][0]
    assert item["doctor_name"] == "Dr Test" and item["acknowledged_at"] is None
    first = client.post(f"/instructions/{nid}/acknowledge", headers=auth(token)).json()
    assert first == client.post(f"/instructions/{nid}/acknowledge", headers=auth(token)).json()
    db = next(client.app.dependency_overrides[get_db]())
    assert db.query(AuditLog).filter_by(action="acknowledge_instruction").count() == 1
    assert client.get(url, headers=auth(doc)).json()["items"][0]["acknowledged_at"]
    client.delete(f"/journeys/{j}/clinicians/{c.id}", headers=auth(token))
    assert client.get(url, headers=auth(doc)).status_code == 404
    assert client.post(url, json={"note": "new"}, headers=auth(doc)).status_code == 404
    assert len(client.get(purl, headers=auth(token)).json()["items"]) == 1
