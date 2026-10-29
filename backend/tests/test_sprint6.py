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
    c = provision(client)
    assert client.post(f"/journeys/{j}/doctor-questions", json={"clinician_id": c.id, "question": "Is my scan date fine?"}, headers=auth(token)).status_code == 409


def test_doctor_question_flow_and_authorization(client):
    token = signup(client)["token"]
    other = signup(client, phone="9000000078")["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token)).json()["journey_id"]
    c = provision(client)
    doc = client.post("/clinician/login", json={"email": c.email, "password": "correct horse 123"}).json()["token"]
    client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c.email}, headers=auth(token))

    # Recipient is required: missing, unknown and ungranted clinician ids are refused.
    assert client.post(f"/journeys/{j}/doctor-questions", json={"question": "No recipient"}, headers=auth(token)).status_code == 422
    assert client.post(f"/journeys/{j}/doctor-questions", json={"clinician_id": "nope", "question": "Unknown doctor"}, headers=auth(token)).status_code == 404
    q = client.post(f"/journeys/{j}/doctor-questions", json={"clinician_id": c.id, "question": "Can we move my scan to Friday?"}, headers=auth(token))
    assert q.status_code == 201
    assert q.json()["recipient_name"] == "Dr Test"
    qid = q.json()["id"]
    assert q.json()["answered"] is False

    # Another patient cannot see the questions.
    assert client.get(f"/journeys/{j}/doctor-questions", headers=auth(other)).status_code == 404
    # Patient sees it listed, with linked doctor names.
    listed = client.get(f"/journeys/{j}/doctor-questions", headers=auth(token)).json()
    assert listed["items"][0]["question"] == "Can we move my scan to Friday?"
    assert listed["linked_doctors"] == [{"id": c.id, "name": "Dr Test"}]

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
    qid = client.post(f"/journeys/{j}/doctor-questions", json={"clinician_id": c.id, "question": "Where do I collect my report?"}, headers=auth(token)).json()["id"]
    client.delete(f"/journeys/{j}/clinicians/{c.id}", headers=auth(token))
    assert client.get(f"/clinician/journeys/{j}/questions", headers=auth(doc)).status_code == 404
    assert client.post(f"/clinician/questions/{qid}/answer", json={"answer": "At reception."}, headers=auth(doc)).status_code == 404
    # The patient's own record of the exchange survives revocation.
    assert client.get(f"/journeys/{j}/doctor-questions", headers=auth(token)).json()["items"][0]["id"] == qid


def test_assistant_malayalam_advice_defers(client):
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    r = chat(client, token, "എനിക്ക് വയറ്റുവേദനയുണ്ട്, ഏത് മരുന്ന് കഴിക്കാം?", lang="ml").json()
    # വയറ്റുവേദന (abdominal pain) is also a danger-sign keyword; either way it must
    # route to the doctor, never to advice or a corpus answer.
    assert r["source"] in ("danger-sign-rule", "defer-advice")
    assert r["suggest_doctor"] is True


def test_assistant_unsupported_language_falls_back_safely(client):
    # Tamil is not in the en/ml/hi fixed-string set and the test env has no Sarvam
    # key, so no translation pass is possible: the safe fallback must fire.
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    r = chat(client, token, "கர்ப்பத்தில் என்ன சாப்பிடலாம்?", lang="ta").json()
    assert r["source"] == "no-translation"
    assert r["suggest_doctor"] is True


def test_assistant_curated_verbatim_without_sarvam(client, monkeypatch):
    # No key configured: the answer must be the curated corpus text itself,
    # not model-generated wording. (Model rewording is prompt-constrained only
    # and is covered by review, not by this test.)
    from app import voice
    assert not voice.available()
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    r = chat(client, token, "What is the anomaly scan?").json()
    assert r["source"].startswith("guidance:")
    from app.guidance import CORPUS
    corpus_texts = " ".join(c["text"] for c in CORPUS)
    assert r["answer"] in corpus_texts


def test_multiple_grants_require_explicit_recipient_and_enforce_it(client):
    token = signup(client)["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token)).json()["journey_id"]
    c1 = provision(client)
    from app.database import get_db
    from app.models import Clinician
    from app.security import hash_password
    db = next(client.app.dependency_overrides[get_db]())
    c2 = Clinician(name="Dr Second", email="doctor2@example.test", password_hash=hash_password("correct horse 123"))
    db.add(c2); db.commit()
    doc1 = client.post("/clinician/login", json={"email": c1.email, "password": "correct horse 123"}).json()["token"]
    doc2 = client.post("/clinician/login", json={"email": c2.email, "password": "correct horse 123"}).json()["token"]
    client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c1.email}, headers=auth(token))
    client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c2.email}, headers=auth(token))

    listed = client.get(f"/journeys/{j}/doctor-questions", headers=auth(token)).json()
    assert {d["name"] for d in listed["linked_doctors"]} == {"Dr Test", "Dr Second"}

    # The question goes to the chosen recipient only - no broadcast.
    q = client.post(f"/journeys/{j}/doctor-questions", json={"clinician_id": c2.id, "question": "Is fasting needed before the test?"}, headers=auth(token))
    assert q.status_code == 201 and q.json()["recipient_name"] == "Dr Second"
    qid = q.json()["id"]

    # The other granted clinician can review the journey but CANNOT see the question.
    assert client.get(f"/clinician/journeys/{j}/questions", headers=auth(doc1)).json()["items"] == []
    # ...and cannot answer it (404, existence not revealed).
    assert client.post(f"/clinician/questions/{qid}/answer", json={"answer": "sneaky"}, headers=auth(doc1)).status_code == 404
    # The recipient sees and answers it.
    assert client.get(f"/clinician/journeys/{j}/questions", headers=auth(doc2)).json()["items"][0]["id"] == qid
    client.post(f"/clinician/questions/{qid}/answer", json={"answer": "Yes, 8 hours."}, headers=auth(doc2))
    got = client.get(f"/journeys/{j}/doctor-questions", headers=auth(token)).json()["items"][0]
    assert got["answerer_name"] == "Dr Second" and got["recipient_name"] == "Dr Second"
    # Roster counts only questions addressed to that clinician.
    assert client.get("/clinician/patients", headers=auth(doc1)).json()["open_questions_total"] == 0
    assert client.get("/clinician/patients", headers=auth(doc2)).json()["open_questions_total"] == 0  # answered now


def test_assistant_does_not_call_generative_chat_model(client, monkeypatch):
    # Even with a Sarvam key configured, this assistant never uses the
    # free-form chat model: curated text verbatim + display translation only.
    from app import voice
    calls = []
    monkeypatch.setattr(voice, "available", lambda: True)
    monkeypatch.setattr(voice, "chat", lambda *a, **k: calls.append((a, k)) or "REWORDED")
    monkeypatch.setattr(voice, "translate", lambda text, lang, source_language="en", db=None: f"[{lang}] {text}")
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    r = chat(client, token, "What is the anomaly scan?", lang="ml").json()
    assert r["source"].startswith("guidance:")
    assert "REWORDED" not in r["answer"]
    assert calls == []


def test_export_and_delete_cover_chat_and_questions(client):
    token = signup(client, phone="9000000090")["token"]
    j = client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token)).json()["journey_id"]
    c = provision(client)
    client.post(f"/journeys/{j}/clinicians", json={"clinician_email": c.email}, headers=auth(token))
    chat(client, token, "When is my next appointment?")
    client.post(f"/journeys/{j}/doctor-questions", json={"clinician_id": c.id, "question": "Where is the lab?"}, headers=auth(token))

    export = client.get("/me/export", headers=auth(token))
    assert export.status_code == 200
    import json as jsonlib
    data = jsonlib.loads(export.content)
    assert any(m["role"] == "user" and "appointment" in m["text"] for m in data["assistant_chat"])
    assert data["journeys"][0]["doctor_questions"][0]["question"] == "Where is the lab?"

    assert client.request("DELETE", "/me", json={"confirm": "DELETE"}, headers=auth(token)).status_code == 200
    from app.database import get_db
    from app.models import AssistantMessage, DoctorQuestion
    db = next(client.app.dependency_overrides[get_db]())
    assert db.query(AssistantMessage).count() == 0
    assert db.query(DoctorQuestion).count() == 0
