from app.database import get_db
from app.models import AuditLog, Clinician, JourneyClinicianGrant
from app.security import hash_password
from tests.conftest import auth, signup


def provision(client):
    db = next(client.app.dependency_overrides[get_db]())
    c = Clinician(name="Dr Test", email="doctor@example.test", password_hash=hash_password("correct horse 123"))
    db.add(c)
    db.commit()
    return c


def test_clinician_login_grants_signoff_and_revocation(client):
    p = signup(client)["token"]
    journey = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(p)).json()
    j = journey["journey_id"]
    milestone = journey["milestones"][0]["id"]
    c = provision(client)
    assert client.post("/clinician/login", json={"email": c.email, "password": "wrong"}).status_code == 401
    doc = client.post("/clinician/login", json={"email": c.email, "password": "correct horse 123"}).json()["token"]
    body = {"journey_id": j, "milestone_id": milestone, "note": "Reviewed test milestone"}
    assert client.post("/clinician/signoffs", json=body, headers=auth(p)).status_code == 401
    assert client.post("/clinician/signoffs", json=body, headers=auth(doc)).status_code == 404
    assert client.get(f"/clinician/journeys/{j}/review", headers=auth(doc)).status_code == 404
    assert client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c.email}, headers=auth(doc)).status_code == 401
    r = client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c.email}, headers=auth(p))
    assert r.status_code == 201
    assert client.get(f"/clinician/journeys/{j}/review", headers=auth(doc)).status_code == 200
    r = client.post("/clinician/signoffs", json=body, headers={**auth(doc), "X-Doctor-Name": "Impostor"})
    assert r.status_code == 201, r.text
    assert r.json()["doctor_name"] == c.name
    after = client.get(f"/journey/{j}", headers=auth(p)).json()
    assert after["milestones"][0]["signoff"]["doctor_name"] == c.name
    db = next(client.app.dependency_overrides[get_db]())
    assert db.query(AuditLog).filter(AuditLog.actor == f"clinician:{c.id}", AuditLog.action == "signoff").count() == 1
    assert client.delete(f"/journeys/{j}/clinicians/{c.id}", headers=auth(p)).status_code == 200
    assert client.post("/clinician/signoffs", json=body, headers=auth(doc)).status_code == 404


def test_other_patient_cannot_grant_or_access_review(client):
    a = signup(client)["token"]
    b = signup(client, phone="9000000066")["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(a)).json()["journey_id"]
    c = provision(client)
    assert client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c.email}, headers=auth(b)).status_code == 404
    assert client.post(f"/journeys/{j}/clinicians", json={"clinician_email": "none@example.test"}, headers=auth(a)).status_code == 404
