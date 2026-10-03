from datetime import date

from tests.conftest import auth, signup
from tests.test_clinicians import provision


def _ms(client, token, j, key="anomaly_scan"):
    return [m for m in client.get(f"/journey/{j}", headers=auth(token)).json()["milestones"]]


def test_completion_levels(client):
    token = signup(client)["token"]
    j = client.post("/journeys", json={"lmp": "2026-05-01"}, headers=auth(token)).json()["journey_id"]
    ms = _ms(client, token, j)
    assert all(m["completion_level"] is None for m in ms)
    mid = ms[0]["id"]
    client.post(f"/milestones/{mid}/complete", json={}, headers=auth(token))
    assert next(m for m in _ms(client, token, j) if m["id"] == mid)["completion_level"] == "self_reported"
    # confirmed report linked to the milestone -> evidence_confirmed
    up = client.post(f"/journeys/{j}/documents/upload", data={"milestone_id": mid},
                     files={"file": ("r.txt", b"Hemoglobin 11.5 g/dL", "text/plain")}, headers=auth(token)).json()
    client.post(f"/documents/{up['document_id']}/extract", headers=auth(token))
    assert next(m for m in _ms(client, token, j) if m["id"] == mid)["completion_level"] == "self_reported"
    r = client.post(f"/documents/{up['document_id']}/confirm", json={"milestone_id": mid, "values": [
        {"code": "hb", "value": 11.5, "unit": "g/dL", "observed_on": date.today().isoformat()}]}, headers=auth(token))
    assert r.status_code == 200, r.text
    assert next(m for m in _ms(client, token, j) if m["id"] == mid)["completion_level"] == "evidence_confirmed"
    # clinician sign-off -> clinician_verified
    c = provision(client)
    doc = client.post("/clinician/login", json={"email": c.email, "password": "correct horse 123"}).json()["token"]
    client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c.email}, headers=auth(token))
    s = client.post("/clinician/signoffs", json={"journey_id": j, "milestone_id": mid, "note": "ok"}, headers=auth(doc))
    assert s.status_code == 201, s.text
    assert next(m for m in _ms(client, token, j) if m["id"] == mid)["completion_level"] == "clinician_verified"
# re-trigger checks
