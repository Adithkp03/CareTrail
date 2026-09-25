from datetime import date, timedelta

from app.seed import DEMO_PASSWORD, DEMO_PHONE
from app.database import get_db
from app.models import Clinician, JourneyClinicianGrant
from app.security import hash_password

from .conftest import auth, signup


def test_health(client):
    assert client.get("/health").json()["status"] == "ok"


def test_signup_login_me(client):
    data = signup(client)
    me = client.get("/auth/me", headers=auth(data["token"]))
    assert me.status_code == 200
    assert me.json()["phone"] == "9876543210"

    login = client.post("/auth/login", json={"phone": "9876543210", "password": "secret123"})
    assert login.status_code == 200
    bad = client.post("/auth/login", json={"phone": "9876543210", "password": "wrong"})
    assert bad.status_code == 401


def test_patient_creates_own_journey_from_template(client):
    token = signup(client)["token"]
    lmp = (date.today() - timedelta(days=100)).isoformat()
    r = client.post("/journeys", json={"lmp": lmp}, headers=auth(token))
    assert r.status_code == 201, r.text
    body = r.json()
    assert body["template_id"] == "antenatal"
    assert body["gestational_age"]["weeks"] == 14
    assert len(body["milestones"]) == 14
    # nothing completed yet: opened windows are "now" (early ones overdue), the rest "next"
    assert {m["status"] for m in body["milestones"]} == {"now", "next"}
    first = next(m for m in body["milestones"] if m["key"] == "first_consultation")
    assert first["status"] == "now" and first["overdue"] is True

    listing = client.get("/journeys", headers=auth(token)).json()
    assert [j["journey_id"] for j in listing["journeys"]] == [body["journey_id"]]


def test_journey_needs_lmp_or_edd(client):
    token = signup(client)["token"]
    r = client.post("/journeys", json={}, headers=auth(token))
    assert r.status_code == 422


def test_journeys_are_private_between_patients(client):
    token_a = signup(client, phone="9000000010")["token"]
    token_b = signup(client, phone="9000000011", name="Other Mother")["token"]
    journey_id = client.post(
        "/journeys", json={"lmp": (date.today() - timedelta(days=90)).isoformat()}, headers=auth(token_a)
    ).json()["journey_id"]

    assert client.get(f"/journey/{journey_id}", headers=auth(token_b)).status_code == 404
    milestone_id = client.get(f"/journey/{journey_id}", headers=auth(token_a)).json()["milestones"][0]["id"]
    assert client.get(f"/milestones/{milestone_id}", headers=auth(token_b)).status_code == 404
    assert client.get("/journeys", headers=auth(token_b)).json()["journeys"] == []


def test_anjali_seed_matches_the_example(client):
    seed = client.post("/demo/seed").json()
    login = client.post("/auth/login", json={"phone": DEMO_PHONE, "password": DEMO_PASSWORD})
    token = login.json()["token"]
    body = client.get(f"/journey/{seed['journey_id']}", headers=auth(token)).json()

    # Anjali, Malayalam-speaking, at 22w0d
    assert body["patient"]["name"] == "Anjali"
    assert body["patient"]["language"] == "ml"
    assert (body["gestational_age"]["weeks"], body["gestational_age"]["plus_days"]) == (22, 0)

    status = {m["key"]: m["status"] for m in body["milestones"]}
    # The example line: first consultation and blood tests completed,
    # second-trimester review is the current stage,
    # anomaly scan upcoming, next consultation already scheduled.
    assert status["first_consultation"] == "done"
    assert status["baseline_bloods"] == "done"
    assert status["nt_scan"] == "done"
    assert status["second_trimester_review"] == "now"
    assert status["anomaly_scan"] == "upcoming"
    assert status["consultation_24w"] == "upcoming"
    assert status["ogtt"] == "next"

    assert body["next_appointment"]["key"] == "anomaly_scan"
    assert body["flags"] == []  # seeded baseline values are normal
    assert body["danger_signs"]


def test_complete_and_schedule_flows_update_status(client):
    token = signup(client)["token"]
    lmp = (date.today() - timedelta(days=154)).isoformat()
    journey = client.post("/journeys", json={"lmp": lmp}, headers=auth(token)).json()
    by_key = {m["key"]: m for m in journey["milestones"]}

    r = client.post(
        f"/milestones/{by_key['second_trimester_review']['id']}/complete",
        json={"notes": "all well"},
        headers=auth(token),
    )
    assert r.status_code == 200
    updated = {m["key"]: m for m in r.json()["milestones"]}
    assert updated["second_trimester_review"]["status"] == "done"

    future = (date.today() + timedelta(days=3)).isoformat()
    r = client.post(
        f"/milestones/{by_key['anomaly_scan']['id']}/schedule",
        json={"scheduled_date": future},
        headers=auth(token),
    )
    assert {m["key"]: m for m in r.json()["milestones"]}["anomaly_scan"]["status"] == "upcoming"


def test_flags_come_from_template_thresholds_not_an_llm(client):
    from app.database import get_db
    from app.models import Observation

    seed = client.post("/demo/seed").json()
    token = client.post("/auth/login", json={"phone": DEMO_PHONE, "password": DEMO_PASSWORD}).json()["token"]
    journey = client.get(f"/journey/{seed['journey_id']}", headers=auth(token)).json()
    bloods = next(m for m in journey["milestones"] if m["key"] == "baseline_bloods")

    # write a low haemoglobin straight into the store, as the phase-3 extractor would
    db = next(iter(client.app.dependency_overrides[get_db]()))
    db.add(
        Observation(
            journey_id=journey["journey_id"],
            milestone_id=bloods["id"],
            code="hb",
            value=9.8,
            unit="g/dL",
            observed_on=date.today(),
            source="extracted",
        )
    )
    db.commit()

    flags = client.get("/flags", params={"journey_id": journey["journey_id"]}, headers=auth(token)).json()["flags"]
    assert len(flags) == 1
    flag = flags[0]
    assert flag["code"] == "hb"
    assert flag["value"] == 9.8
    assert flag["threshold"] == {"min": 11.0}
    assert "anaemia" in flag["message"]


def test_patient_cannot_sign_off_by_typing_doctor_name(client):
    seed = client.post("/demo/seed").json()
    token = client.post("/auth/login", json={"phone": DEMO_PHONE, "password": DEMO_PASSWORD}).json()["token"]
    r = client.post("/signoffs", json={"journey_id": seed["journey_id"]}, headers={**auth(token), "X-Doctor-Name": "Dr Any Name"})
    assert r.status_code == 403


def test_demo_seed_is_idempotent_and_resets_state(client):
    """Clicking 'Try the demo' twice must not 500, and must restore Anjali's start state."""
    first = client.post("/demo/seed")
    assert first.status_code == 200
    token = client.post("/auth/login", json={"phone": DEMO_PHONE, "password": DEMO_PASSWORD}).json()["token"]
    jid = first.json()["journey_id"]
    journey = client.get(f"/journey/{jid}", headers=auth(token)).json()
    review = next(m for m in journey["milestones"] if m["key"] == "second_trimester_review")
    client.post(f"/milestones/{review['id']}/complete", json={}, headers=auth(token))

    second = client.post("/demo/seed")
    assert second.status_code == 200, second.text
    fresh_jid = second.json()["journey_id"]
    fresh = client.get(f"/journey/{fresh_jid}", headers=auth(token)).json()
    status = {m["key"]: m["status"] for m in fresh["milestones"]}
    assert status["second_trimester_review"] == "now"  # reset, not still done
    assert status["anomaly_scan"] == "upcoming"
    assert (fresh["gestational_age"]["weeks"], fresh["gestational_age"]["plus_days"]) == (22, 0)


def test_demo_reset_removes_old_journey_clinician_grant(client):
    first = client.post("/demo/seed")
    assert first.status_code == 200
    old_jid = first.json()["journey_id"]
    db = next(client.app.dependency_overrides[get_db]())
    clinician = Clinician(name="Dr Demo", email="demo-doctor@example.test", password_hash=hash_password("test-password-123"))
    db.add(clinician)
    db.flush()
    db.add(JourneyClinicianGrant(journey_id=old_jid, clinician_id=clinician.id))
    db.commit()

    second = client.post("/demo/seed")
    assert second.status_code == 200, second.text
    assert second.json()["journey_id"] != old_jid
    assert db.query(JourneyClinicianGrant).filter_by(journey_id=old_jid).count() == 0
    # Never silently carry a patient's grant to a new journey.
    assert db.query(JourneyClinicianGrant).filter_by(journey_id=second.json()["journey_id"]).count() == 0


def test_first_visit_doctor_notes_are_present_in_new_journey(client):
    token = signup(client, phone="9000000030")["token"]
    journey = client.post("/journeys", json={"lmp": (date.today() - timedelta(days=50)).isoformat()}, headers=auth(token)).json()
    by_key = {m["key"]: m for m in journey["milestones"]}
    assert "positive urine pregnancy test" in by_key["first_consultation"]["prep_notes"]
    assert "site of pregnancy" in by_key["first_consultation"]["prep_notes"]
    tests = by_key["baseline_bloods"]["prep_notes"]
    for term in ("hemoglobin", "platelets", "blood group", "HIV/VDRL/HBsAg", "TFT (fasting)", "fasting/post-prandial sugars", "urine routine microscopy and culture"):
        assert term in tests


def test_existing_journey_receives_revised_nt_window_and_notes(client):
    token = signup(client)["token"]
    lmp = (date.today() - timedelta(days=97)).isoformat()
    created = client.post("/journeys", json={"lmp": lmp}, headers=auth(token)).json()
    nt = next(m for m in created["milestones"] if m["key"] == "nt_scan")
    # Detail and timeline use the same revised pathway wording.
    assert nt["status"] == "now" and not nt["overdue"]
    assert "PAPP-A" in nt["prep_notes"]
    detail = client.get(f"/milestones/{nt['id']}", headers=auth(token)).json()
    assert detail["window_weeks"] == nt["window_weeks"]
    assert "13+6" in detail["prep_notes"]
