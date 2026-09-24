"""Supabase Auth bridge: POST /auth/supabase exchanges a Supabase access token
for a CareTrail session, provisioning the patient on first sign-in."""
import time

import jwt
import pytest

SECRET = "test-jwt-secret"


def _sb_token(sub="user-uuid-1", email="amma@example.com", phone="", name="Amma", secret=SECRET, aud="authenticated"):
    return jwt.encode(
        {
            "sub": sub,
            "aud": aud,
            "email": email,
            "phone": phone,
            "user_metadata": {"name": name},
            "exp": int(time.time()) + 3600,
        },
        secret,
        algorithm="HS256",
    )


@pytest.fixture()
def configured(monkeypatch):
    monkeypatch.setattr("app.config.SUPABASE_JWT_SECRET", SECRET)
    monkeypatch.setattr("app.supabase_auth.SUPABASE_JWT_SECRET", SECRET)
    yield


def test_unconfigured_returns_501(client):
    r = client.post("/auth/supabase", json={"access_token": "x", "consent": True})
    assert r.status_code == 501


def test_bad_token_401(client, configured):
    r = client.post("/auth/supabase", json={"access_token": _sb_token(secret="wrong"), "consent": True})
    assert r.status_code == 401


def test_first_signin_requires_consent(client, configured):
    r = client.post("/auth/supabase", json={"access_token": _sb_token()})
    assert r.status_code == 422


def test_first_signin_provisions_and_returns_session(client, configured):
    r = client.post("/auth/supabase", json={"access_token": _sb_token(), "consent": True})
    assert r.status_code == 200, r.text
    token = r.json()["token"]
    me = client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me.status_code == 200
    assert me.json()["name"] == "Amma"


def test_returning_user_gets_fresh_session(client, configured):
    r1 = client.post("/auth/supabase", json={"access_token": _sb_token(), "consent": True})
    r2 = client.post("/auth/supabase", json={"access_token": _sb_token()})  # no consent needed now
    assert r2.status_code == 200
    assert r1.json()["patient_id"] == r2.json()["patient_id"]


def test_legacy_phone_password_login_still_works(client, configured):
    from tests.conftest import signup

    token = signup(client)["token"]
    me = client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me.status_code == 200
