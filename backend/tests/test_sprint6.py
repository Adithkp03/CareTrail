"""Sprint 6: scoped general-help assistant + patient->doctor questions.

The assistant must never give individualized medical advice: danger signs route
to "contact your doctor now", advice-seeking questions get a fixed deferral,
app-help questions are deterministic, and general info comes only from the
curated corpus.
"""
from tests.conftest import auth, signup
from tests.test_clinicians import provision


def chat(client, token, message, lang="en"):
    return client.post("/assistant/chat", json={"message": message, "lang": lang}, headers=auth(token))


def test_assistant_danger_signs_are_urgent_and_suggest_doctor(client):
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    r = chat(client, token, "I have bleeding and a headache, what do I do?")
    assert r.status_code == 201
    body = r.json()
    assert body["urgent"] is True and body["suggest_doctor"] is True
    assert body["source"] == "danger-sign-rule"
    assert "doctor" in body["answer"].lower()


def test_assistant_never_gives_medical_advice(client):
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    for q in [
        "Can I take paracetamol for my back pain?",
        "I feel dizzy and nauseous since morning, is it normal?",
        "What medicine should I take for constipation?",
        "My baby is not moving as much, what tablet helps?",
    ]:
        r = chat(client, token, q).json()
        assert r["urgent"] is False or r["source"] == "danger-sign-rule", q
        if r["source"] != "danger-sign-rule":
            assert r["source"] == "defer-advice", q
            assert r["suggest_doctor"] is True
            assert "doctor" in r["answer"].lower()


def test_assistant_app_help_is_deterministic(client):
    token = signup(client)["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token)).json()
    milestones = j["milestones"]
    mid = milestones[1]["id"]
    client.post(f"/milestones/{mid}/schedule", json={"scheduled_date": "2026-11-05"}, headers=auth(token))
    r = chat(client, token, "When is my next appointment?").json()
    assert r["source"] == "app:appointments" and r["suggest_doctor"] is False
    assert "2026-11-05" in r["answer"]
    r = chat(client, token, "Do I have any reminders?").json()
    assert r["source"] == "app:reminders"
    r = chat(client, token, "How do I upload a report?").json()
    assert r["source"] in ("app:reports", "app:upload")


def test_assistant_general_info_uses_curated_corpus(client):
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    r = chat(client, token, "What is the anomaly scan?").json()
    assert r["source"].startswith("guidance:") or r["source"].startswith("app:"), r
    assert "anomaly" in r["answer"].lower() or "scan" in r["answer"].lower()
    assert r["suggest_doctor"] is False
    r = chat(client, token, "What does haemoglobin below 11 mean in general?").json()
    assert r["source"].startswith("guidance:"), r


def test_assistant_unknown_defers_with_doctor_route(client):
    token = signup(client)["token"]
    r = chat(client, token, "Tell me a joke about staplers").json()
    assert r["source"] == "none" and r["suggest_doctor"] is True


def test_assistant_history_roundtrip_and_clear(client):
    token = signup(client)["token"]
    other = signup(client, phone="9000000077")["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    chat(client, token, "When is my next appointment?")
    chat(client, token, "What is the anomaly scan?")
    items = client.get("/assistant/history", headers=auth(token)).json()["items"]
    assert [i["role"] for i in items] == ["user", "assistant", "user", "assistant"]
    assert items[0]["text"] == "When is my next appointment?"
    # History is owner-scoped.
    assert client.get("/assistant/history", headers=auth(other)).json()["items"] == []
    deleted = client.delete("/assistant/history", headers=auth(token)).json()["deleted"]
    assert deleted == 4
    assert client.get("/assistant/history", headers=auth(token)).json()["items"] == []


def test_doctor_question_requires_grant(client):
    token = signup(client)["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token)).json()["journey_id"]
    provision(client)
    assert client.post(f"/journeys/{j}/doctor-questions", json={"question": "Is my scan date fine?"}, headers=auth(token)).status_code == 409


def test_doctor_question_flow_and_authorization(client):
    token = signup(client)["token"]
    other = signup(client, phone="9000000078")["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token)).json()["journey_id"]
    c = provision(client)
    doc = client.post("/clinician/login", json={"email": c.email, "password": "correct horse 123"}).json()["token"]
    client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c.email}, headers=auth(token))

    q = client.post(f"/journeys/{j}/doctor-questions", json={"question": "Can we move my scan to Friday?"}, headers=auth(token))
    assert q.status_code == 201
    qid = q.json()["id"]
    assert q.json()["answered"] is False

    # Another patient cannot see the questions.
    assert client.get(f"/journeys/{j}/doctor-questions", headers=auth(other)).status_code == 404
    # Patient sees it listed, with linked doctor names.
    listed = client.get(f"/journeys/{j}/doctor-questions", headers=auth(token)).json()
    assert listed["items"][0]["question"] == "Can we move my scan to Friday?"
    assert listed["linked_doctors"] == [{"name": "Dr Test"}]

    # Clinician sees and answers; roster reflects the open question.
    roster = client.get("/clinician/patients", headers=auth(doc)).json()
    assert roster["items"][0]["open_questions"] == 1 and roster["open_questions_total"] == 1
    assert client.get(f"/clinician/journeys/{j}/questions", headers=auth(doc)).json()["items"][0]["id"] == qid
    # Patients cannot use the clinician answer endpoint.
    assert client.post(f"/clinician/questions/{qid}/answer", json={"answer": "x"}, headers=auth(token)).status_code == 401
    ans = client.post(f"/clinician/questions/{qid}/answer", json={"answer": "Friday 10 am works."}, headers=auth(doc))
    assert ans.status_code == 200 and ans.json()["answerer_name"] == "Dr Test"
    got = client.get(f"/journeys/{j}/doctor-questions", headers=auth(token)).json()["items"][0]
    assert got["answered"] is True and got["answer"] == "Friday 10 am works." and got["answered_at"]
    assert client.get("/clinician/patients", headers=auth(doc)).json()["open_questions_total"] == 0


def test_doctor_question_revocation_blocks_answer(client):
    token = signup(client)["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token)).json()["journey_id"]
    c = provision(client)
    doc = client.post("/clinician/login", json={"email": c.email, "password": "correct horse 123"}).json()["token"]
    client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c.email}, headers=auth(token))
    qid = client.post(f"/journeys/{j}/doctor-questions", json={"question": "Where do I collect my report?"}, headers=auth(token)).json()["id"]
    client.delete(f"/journeys/{j}/clinicians/{c.id}", headers=auth(token))
    assert client.get(f"/clinician/journeys/{j}/questions", headers=auth(doc)).status_code == 404
    assert client.post(f"/clinician/questions/{qid}/answer", json={"answer": "At reception."}, headers=auth(doc)).status_code == 404
    # The patient's own record of the exchange survives revocation.
    assert client.get(f"/journeys/{j}/doctor-questions", headers=auth(token)).json()["items"][0]["id"] == qid
