from tests.conftest import auth, signup
from tests.test_clinicians import provision


def test_clinician_patient_list_only_granted(client):
    a = signup(client)["token"]
    b = signup(client, phone="9000000085")["token"]
    ja = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(a)).json()["journey_id"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(b))
    c = provision(client)
    doc = client.post("/clinician/login", json={"email": c.email, "password": "correct horse 123"}).json()["token"]
    assert client.get("/clinician/patients").status_code == 401
    assert client.get("/clinician/patients", headers=auth(a)).status_code == 401
    assert client.get("/clinician/patients", headers=auth(doc)).json()["items"] == []
    client.post(f"/journeys/{ja}/clinicians", json={"clinician_email": c.email}, headers=auth(a))
    up = client.post(f"/journeys/{ja}/documents/upload", files={"file": ("r.txt", b"Hemoglobin 8.0 g/dL", "text/plain")}, headers=auth(a)).json()
    client.post(f"/documents/{up['document_id']}/extract", headers=auth(a))
    client.post(f"/documents/{up['document_id']}/confirm", json={"values": [{"code": "hb", "value": 8.0, "unit": "g/dL"}]}, headers=auth(a))
    client.post(f"/clinician/journeys/{ja}/instructions", json={"note": "Iron"}, headers=auth(doc))
    r = client.get("/clinician/patients", headers=auth(doc)).json()
    assert len(r["items"]) == 1 and r["items"][0]["journey_id"] == ja
    assert r["items"][0]["pending_reviews"] >= 1 and r["items"][0]["instructions_waiting"] == 1 and r["pending_total"] >= 1
# re-trigger checks
