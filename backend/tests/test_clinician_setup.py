from datetime import datetime, timedelta, timezone

from app.clinician_setup import mint
from app.database import get_db
from app.models import Clinician, ClinicianSetupToken
from app.security import hash_password


def test_setup_single_use_and_login(client):
    db = next(client.app.dependency_overrides[get_db]())
    clinician = Clinician(name="Dr Example", email="doctor@example.test", password_hash=hash_password("unshared random original"))
    db.add(clinician)
    db.commit()
    raw = mint(db, clinician)
    assert raw not in str(db.query(ClinicianSetupToken).first().__dict__)
    assert client.post("/clinician/login", json={"email": clinician.email, "password": "chosen secure password"}).status_code == 401
    r = client.post("/clinician/setup-password", json={"token": raw, "password": "chosen secure password"})
    assert r.status_code == 200, r.text
    assert client.post("/clinician/setup-password", json={"token": raw, "password": "another secure password"}).status_code == 400
    assert client.post("/clinician/login", json={"email": clinician.email, "password": "chosen secure password"}).status_code == 200
    assert client.post("/clinician/login", json={"email": clinician.email, "password": "unshared random original"}).status_code == 401


def test_expired_short_and_rotated_setup_links(client):
    db = next(client.app.dependency_overrides[get_db]())
    clinician = Clinician(name="Dr Example", email="doctor@example.test", password_hash=hash_password("unshared random original"))
    db.add(clinician); db.commit()
    old = mint(db, clinician)
    new = mint(db, clinician)
    assert client.post("/clinician/setup-password", json={"token": old, "password": "chosen secure password"}).status_code == 400
    assert client.post("/clinician/setup-password", json={"token": new, "password": "short"}).status_code == 400
    row = db.query(ClinicianSetupToken).first()
    row.expires_at = datetime.now(timezone.utc) - timedelta(minutes=1)
    db.commit()
    assert client.post("/clinician/setup-password", json={"token": new, "password": "chosen secure password"}).status_code == 400
    assert client.post("/clinician/login", json={"email": clinician.email, "password": "unshared random original"}).status_code == 200
