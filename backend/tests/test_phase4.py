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
    client.post(f"/documents/{doc}/extract", headers=auth(token))
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


def test_sarvam_language_codes_cover_new_tts_and_speech_input():
    from app.voice import LANG_NAMES
    assert {code: LANG_NAMES[code] for code in ("ta", "te", "kn", "bn", "mr")} == {
        "ta": "ta-IN", "te": "te-IN", "kn": "kn-IN", "bn": "bn-IN", "mr": "mr-IN",
    }


def test_sarvam_tts_request_selects_language(monkeypatch):
    from app import voice
    monkeypatch.setenv("SARVAM_API_KEY", "test-only")
    sent = []
    class Response:
        def raise_for_status(self): pass
        def json(self): return {"audios": ["SUQz"]}
    class Client:
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def post(self, path, json):
            sent.append((path, json))
            return Response()
    monkeypatch.setattr(voice, "_client", lambda: Client())
    for code in ("ta", "te", "kn", "bn", "mr"):
        assert voice.tts("Hello", code) == b"ID3"
    assert [x[1]["target_language_code"] for x in sent] == ["ta-IN", "te-IN", "kn-IN", "bn-IN", "mr-IN"]


def test_sarvam_stt_request_selects_language(monkeypatch):
    from app import voice
    monkeypatch.setenv("SARVAM_API_KEY", "test-only")
    sent = []
    class Response:
        def raise_for_status(self): pass
        def json(self): return {"transcript": "hello"}
    class Client:
        def __init__(self, **kw): pass
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def post(self, path, files, data):
            sent.append(data["language_code"])
            return Response()
    monkeypatch.setattr("httpx.Client", Client)
    for code in ("ta", "te", "kn", "bn", "mr"):
        assert voice.stt(b"RIFF", "sample.wav", code) == "hello"
    assert sent == ["ta-IN", "te-IN", "kn-IN", "bn-IN", "mr-IN"]


def test_audio_uses_exact_wording_revision_not_stale_cache(monkeypatch):
    """Regression: the audio endpoint must speak the same explanation text the
    screen shows. A loose prefix match over ExplanationCache can pick a row
    cached from older clinical wording, so audio and text disagree."""
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker
    from sqlalchemy.pool import StaticPool

    from app import explanations
    from app.database import Base
    from app.models import ExplanationCache

    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    db = sessionmaker(bind=engine)()

    db.add(ExplanationCache(cache_key="anomaly_scan|ml|v1|currentrev", text="CURRENT ultrasound wording", language="ml", provider="curated", audio_path=""))
    db.commit()
    # Stale row inserted later: under the old id-desc prefix query it would win.
    db.add(ExplanationCache(cache_key="anomaly_scan|ml|v0|oldrev", text="STALE newborn wording", language="ml", provider="sarvam", audio_path=""))
    db.commit()

    monkeypatch.setenv("SARVAM_API_KEY", "test-key")
    seen = {}

    def fake_tts(text, language):
        seen["text"] = text
        return b"RIFF\x24\x00\x00\x00WAVE"

    monkeypatch.setattr(explanations.voice, "tts", fake_tts)
    audio = explanations.explanation_audio(db, "anomaly_scan|ml|v1|currentrev", "ml")
    assert audio is not None and seen["text"] == "CURRENT ultrasound wording"


def test_languages_derive_from_one_cached_english_source(client, monkeypatch):
    """Source binding: the first regional request creates one English row; every
    other language translates THAT text, never a fresh LLM generation."""
    from app import explanations

    monkeypatch.setenv("SARVAM_API_KEY", "test-key")
    calls = {"chat": 0, "translate_inputs": []}

    def fake_chat(prompt, db=None, patient_id=None):
        calls["chat"] += 1
        return f"Generated English variant {calls['chat']}"

    def fake_translate(text, target_language, source_language="en", db=None):
        calls["translate_inputs"].append(text)
        return f"[{target_language}] {text}"

    monkeypatch.setattr(explanations.voice, "chat", fake_chat)
    monkeypatch.setattr(explanations.voice, "translate", fake_translate)

    token = signup(client)["token"]
    j = make_journey(client, token)
    mid = milestone_id(client, token, j, "anomaly_scan")

    ml = client.get(f"/milestones/{mid}/explanation", params={"lang": "ml"}, headers=auth(token)).json()
    hi = client.get(f"/milestones/{mid}/explanation", params={"lang": "hi"}, headers=auth(token)).json()

    assert calls["chat"] == 1  # English generated exactly once for this wording
    assert calls["translate_inputs"] == ["Generated English variant 1", "Generated English variant 1"]
    assert ml["text"] == "[ml] Generated English variant 1"
    assert hi["text"] == "[hi] Generated English variant 1"

    # A later English request serves the same cached source row.
    en = client.get(f"/milestones/{mid}/explanation", params={"lang": "en"}, headers=auth(token)).json()
    assert en["text"] == "Generated English variant 1" and en["cache"] is True
    assert calls["chat"] == 1
