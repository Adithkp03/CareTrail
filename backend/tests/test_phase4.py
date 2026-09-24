"""Phase 4: explanations + cache, translation fallback, Listen/Ask plumbing,
code-matched danger signs. All with no SARVAM_API_KEY (offline path)."""

from tests.conftest import auth, signup


def make_journey(client, token, lmp="2026-04-20"):
    r = client.post("/journeys", json={"lmp": lmp}, headers=auth(token))
    assert r.status_code == 201, r.text
    return r.json()


def milestone_id(client, token, journey, key):
    return next(m["id"] for m in journey["milestones"] if m["key"] == key)


def test_milestone_explanation_english_curated(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    mid = milestone_id(client, token, j, "anomaly_scan")
    r = client.get(f"/milestones/{mid}/explanation", params={"lang": "en"}, headers=auth(token))
    assert r.status_code == 200, r.text
    body = r.json()
    assert "ultrasound" in body["text"].lower() and body["language"] == "en"


def test_explanation_is_cached_not_regenerated(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    mid = milestone_id(client, token, j, "anomaly_scan")
    first = client.get(f"/milestones/{mid}/explanation", headers=auth(token)).json()
    second = client.get(f"/milestones/{mid}/explanation", headers=auth(token)).json()
    assert first["text"] == second["text"]
    assert second["cache"] is True and first["cache"] is False


def test_anomaly_scan_malayalam_curated_translation(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    mid = milestone_id(client, token, j, "anomaly_scan")
    body = client.get(f"/milestones/{mid}/explanation", params={"lang": "ml"}, headers=auth(token)).json()
    assert "അനോമലി സ്കാൻ" in body["text"] or "അൾട്രാസൗണ്ട" in body["text"]


def test_audio_unavailable_without_key(client, monkeypatch):
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    token = signup(client)["token"]
    j = make_journey(client, token)
    mid = milestone_id(client, token, j, "anomaly_scan")
    client.get(f"/milestones/{mid}/explanation", headers=auth(token))
    r = client.get(f"/milestones/{mid}/explanation/audio", headers=auth(token))
    assert r.status_code == 503


def test_ask_answers_milestone_question_from_grounding(client):
    token = signup(client)["token"]
    make_journey(client, token)
    r = client.post("/ask", json={"question": "what is the anomaly scan for?"}, headers=auth(token))
    assert r.status_code == 200, r.text
    body = r.json()
    assert "ultrasound" in body["answer"].lower()
    assert body["source"].startswith("milestone:") and body["urgent"] is False


def test_ask_danger_sign_always_urgent_in_code(client):
    token = signup(client)["token"]
    make_journey(client, token)
    for q in ["I have some bleeding today", "severe headache since morning", "reduced baby movements"]:
        body = client.post("/ask", json={"question": q}, headers=auth(token)).json()
        assert body["urgent"] is True
        assert body["source"] == "danger-sign-rule"
        assert "doctor" in body["answer"].lower()


def test_ask_danger_sign_malayalam(client):
    token = signup(client)["token"]
    make_journey(client, token)
    body = client.post("/ask", json={"question": "എനിക്ക് രക്തസ്രാവം ഉണ്ട്"}, headers=auth(token)).json()
    assert body["urgent"] is True
    assert body["language"] == "ml"
    assert "ഡോക്ടറെ" in body["answer"]


def test_ask_unknown_question_says_ask_doctor(client):
    token = signup(client)["token"]
    make_journey(client, token)
    body = client.post("/ask", json={"question": "will it rain on delivery day?"}, headers=auth(token)).json()
    assert body["source"] == "none"
    assert "ask your doctor" in body["answer"].lower()


def test_ask_answers_flag_question_from_timeline(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc = client.post(
        f"/journeys/{j['journey_id']}/documents/upload",
        files={"file": ("cbc.txt", b"Haemoglobin: 10.2 g/dL\nDate: 2026-09-20", "text/plain")},
        headers=auth(token),
    ).json()["document_id"]
    client.post(f"/documents/{doc}/confirm", json={"values": [{"code": "hb", "value": 10.2, "unit": "g/dL", "observed_on": "2026-09-20"}]}, headers=auth(token))
    body = client.post("/ask", json={"question": "is my haemoglobin ok?"}, headers=auth(token)).json()
    assert body["source"] == "timeline-flag"
    assert "10.2" in body["answer"]


def test_voice_ask_unavailable_without_key(client, monkeypatch):
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    token = signup(client)["token"]
    make_journey(client, token)
    r = client.post("/ask/voice", files={"file": ("q.webm", b"fake-audio", "audio/webm")}, headers=auth(token))
    assert r.status_code == 503


def test_explanation_not_shared_across_patients(client):
    token_a = signup(client, phone="9000000003")["token"]
    token_b = signup(client, phone="9000000004")["token"]
    j = make_journey(client, token_a)
    mid = milestone_id(client, token_a, j, "anomaly_scan")
    assert client.get(f"/milestones/{mid}/explanation", headers=auth(token_b)).status_code == 404


def test_ask_picks_best_milestone_not_first(client):
    token = signup(client)["token"]
    make_journey(client, token)
    body = client.post("/ask", json={"question": "what is the anomaly scan?"}, headers=auth(token)).json()
    assert body["source"] == "milestone:anomaly_scan"
