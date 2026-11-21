"""Google identity choice flow: probe (create=False), explicit create, and
linking a verified Supabase identity to an existing phone/password account."""
import time

import jwt
import pytest

from .conftest import signup

SECRET = "test-jwt-secret"


def _sb_token(sub="google-sub-1", email="amma@example.com", phone="", name="Amma", secret=SECRET, aud="authenticated"):
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


def test_probe_unknown_identity_returns_404(client, configured):
    r = client.post("/auth/supabase", json={"access_token": _sb_token(), "create": False})
    assert r.status_code == 404
    assert r.json()["detail"] == "no_caretrail_account"


def test_probe_never_provisions(client, configured):
    r1 = client.post("/auth/supabase", json={"access_token": _sb_token(sub="probe-sub"), "create": False})
    assert r1.status_code == 404
    # A probe must not create or mutate an account: probing again still misses,
    # and only an explicit create lands the profile.
    r2 = client.post("/auth/supabase", json={"access_token": _sb_token(sub="probe-sub"), "create": False})
    assert r2.status_code == 404
    r3 = client.post("/auth/supabase", json={"access_token": _sb_token(sub="probe-sub"), "consent": True, "create": True})
    assert r3.status_code == 200


def test_probe_known_identity_returns_session(client, configured):
    client.post("/auth/supabase", json={"access_token": _sb_token(), "consent": True})
    r = client.post("/auth/supabase", json={"access_token": _sb_token(), "create": False})
    assert r.status_code == 200


def test_explicit_create_still_requires_consent(client, configured):
    r = client.post("/auth/supabase", json={"access_token": _sb_token(), "create": True})
    assert r.status_code == 422


def test_web_default_unchanged_provisions(client, configured):
    # Clients that omit `create` keep the pre-existing behavior.
    r = client.post("/auth/supabase", json={"access_token": _sb_token(sub="web-sub"), "consent": True})
    assert r.status_code == 200


def test_link_success_then_google_login_lands_on_same_account(client, configured):
    account = signup(client, phone="9876500001", password="secret123")
    r = client.post(
        "/auth/link-supabase",
        json={"access_token": _sb_token(), "phone": "9876500001", "password": "secret123"},
    )
    assert r.status_code == 200, r.text
    assert r.json()["patient_id"] == account["patient_id"]
    # Later plain Google sign-in resolves to the same account.
    r2 = client.post("/auth/supabase", json={"access_token": _sb_token()})
    assert r2.status_code == 200
    assert r2.json()["patient_id"] == account["patient_id"]


def test_link_wrong_password_401(client, configured):
    signup(client, phone="9876500002", password="secret123")
    r = client.post(
        "/auth/link-supabase",
        json={"access_token": _sb_token(sub="sub-b"), "phone": "9876500002", "password": "wrongpass1"},
    )
    assert r.status_code == 401


def test_link_unknown_phone_401(client, configured):
    r = client.post(
        "/auth/link-supabase",
        json={"access_token": _sb_token(sub="sub-c"), "phone": "9000000000", "password": "secret123"},
    )
    assert r.status_code == 401


def test_link_identity_already_linked_elsewhere_409(client, configured):
    client.post("/auth/supabase", json={"access_token": _sb_token(sub="sub-taken"), "consent": True})
    signup(client, phone="9876500003", password="secret123")
    r = client.post(
        "/auth/link-supabase",
        json={"access_token": _sb_token(sub="sub-taken"), "phone": "9876500003", "password": "secret123"},
    )
    assert r.status_code == 409


def test_link_twice_same_identity_is_idempotent(client, configured):
    signup(client, phone="9876500004", password="secret123")
    for _ in range(2):
        r = client.post(
            "/auth/link-supabase",
            json={"access_token": _sb_token(sub="sub-idem"), "phone": "9876500004", "password": "secret123"},
        )
        assert r.status_code == 200, r.text


def test_link_account_already_linked_to_other_identity_409(client, configured):
    signup(client, phone="9876500005", password="secret123")
    r1 = client.post(
        "/auth/link-supabase",
        json={"access_token": _sb_token(sub="sub-first"), "phone": "9876500005", "password": "secret123"},
    )
    assert r1.status_code == 200
    r2 = client.post(
        "/auth/link-supabase",
        json={"access_token": _sb_token(sub="sub-second"), "phone": "9876500005", "password": "secret123"},
    )
    assert r2.status_code == 409


def test_link_unconfigured_returns_501(client):
    signup(client, phone="9876500006", password="secret123")
    r = client.post(
        "/auth/link-supabase",
        json={"access_token": _sb_token(), "phone": "9876500006", "password": "secret123"},
    )
    assert r.status_code == 501
