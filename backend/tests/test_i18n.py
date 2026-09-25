from .conftest import auth, signup


def test_translate_english_is_identity(client):
    tok = signup(client)["token"]
    r = client.post("/i18n/translate", json={"texts": ["Growth scan"], "lang": "en"}, headers=auth(tok))
    assert r.status_code == 200
    assert r.json()["translations"] == {"Growth scan": "Growth scan"}


def test_curated_medical_terms_win_and_unknown_falls_back(client, monkeypatch):
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    tok = signup(client)["token"]
    r = client.post(
        "/i18n/translate",
        json={"texts": ["Anomaly scan (TIFFA)", "High fever", "Something new"], "lang": "ml"},
        headers=auth(tok),
    )
    tr = r.json()["translations"]
    assert tr["Anomaly scan (TIFFA)"] == "അനോമലി സ്കാൻ (TIFFA)"
    assert tr["High fever"] == "കടുത്ത പനി"
    assert tr["Something new"] == "Something new"  # no key -> stays English, never errors


def test_translate_requires_login_and_valid_lang(client):
    assert client.post("/i18n/translate", json={"texts": ["x"], "lang": "ml"}).status_code == 401
    tok = signup(client)["token"]
    assert client.post("/i18n/translate", json={"texts": ["x"], "lang": "fr"}, headers=auth(tok)).status_code == 400


def test_new_language_codes_are_supported_but_untranslated_copy_is_not_invented(client, monkeypatch):
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    tok = signup(client, language="ta")["token"]
    r = client.post("/i18n/translate", json={"texts": ["Anomaly scan (TIFFA)"], "lang": "ta"}, headers=auth(tok))
    assert r.status_code == 200
    assert r.json()["translations"]["Anomaly scan (TIFFA)"] == "Anomaly scan (TIFFA)"


def test_new_language_untranslated_question_has_safe_fallback(client, monkeypatch):
    from app import voice
    monkeypatch.setattr(voice, "translate", lambda *a, **kw: None)
    tok = signup(client, phone="9000000029", language="ta")["token"]
    from datetime import date, timedelta
    client.post("/journeys", json={"lmp": (date.today() - timedelta(days=100)).isoformat()}, headers=auth(tok))
    r = client.post("/ask", json={"question": "என்ன நடக்கிறது?", "lang": "ta"}, headers=auth(tok))
    assert r.status_code == 200
    assert r.json()["source"] == "none"
    assert "doctor" in r.json()["answer"].lower()
