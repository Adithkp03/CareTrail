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
