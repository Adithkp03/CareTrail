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


def test_identity_does_not_link_by_email(client, configured):
    a = client.post("/auth/supabase", json={"access_token": _sb_token(sub="first"), "consent": True}).json()
    b = client.post("/auth/supabase", json={"access_token": _sb_token(sub="second"), "consent": True}).json()
    assert a["patient_id"] != b["patient_id"]


def test_phone_collision_does_not_link_legacy_profile(client, configured):
    from tests.conftest import signup
    signup(client)
    r = client.post("/auth/supabase", json={"access_token": _sb_token(phone="9876543210"), "consent": True})
    assert r.status_code == 409


def test_expired_token_rejected(client, configured):
    token = jwt.encode({"sub": "s", "aud": "authenticated", "exp": int(time.time()) - 1}, SECRET, algorithm="HS256")
    assert client.post("/auth/supabase", json={"access_token": token, "consent": True}).status_code == 401


def test_missing_expiry_rejected(client, configured):
    token = jwt.encode({"sub": "s", "aud": "authenticated"}, SECRET, algorithm="HS256")
    assert client.post("/auth/supabase", json={"access_token": token, "consent": True}).status_code == 401


def test_project_issuer_checked(client, configured, monkeypatch):
    monkeypatch.setattr("app.supabase_auth.SUPABASE_URL", "https://project.example.test")
    assert client.post("/auth/supabase", json={"access_token": _sb_token(), "consent": True}).status_code == 401


def test_asymmetric_jwks(client, monkeypatch):
    from types import SimpleNamespace
    from cryptography.hazmat.primitives.asymmetric import ec
    key = ec.generate_private_key(ec.SECP256R1())
    project = "https://project.example.test"
    calls = []
    class FakeJWKS:
        def get_signing_key_from_jwt(self, token):
            calls.append(token)
            return SimpleNamespace(key=key.public_key())
    monkeypatch.setattr("app.supabase_auth.SUPABASE_URL", project)
    monkeypatch.setattr("app.supabase_auth.SUPABASE_JWT_SECRET", "")
    monkeypatch.setattr("app.supabase_auth._jwks_client", lambda url: FakeJWKS() if url == project else None)
    token = jwt.encode({"sub": "google-user", "aud": "authenticated", "iss": project + "/auth/v1", "exp": int(time.time()) + 60, "email": "test@example.test", "user_metadata": {"full_name": "Google Patient"}}, key, algorithm="ES256", headers={"kid": "test"})
    r = client.post("/auth/supabase", json={"access_token": token, "consent": True})
    assert r.status_code == 200
    me = client.get("/auth/me", headers={"Authorization": "Bearer " + r.json()["token"]})
    assert me.json()["name"] == "Google Patient"
    assert len(calls) == 1


def test_jwks_connection_failure_is_retryable(client, monkeypatch):
    class OfflineJWKS:
        def get_signing_key_from_jwt(self, token):
            raise jwt.PyJWKClientConnectionError("unavailable")
    monkeypatch.setattr("app.supabase_auth.SUPABASE_URL", "https://project.example.test")
    monkeypatch.setattr("app.supabase_auth._jwks_client", lambda url: OfflineJWKS())
    # Header chooses the configured key path; an actual signature is unnecessary
    # because fetching the trusted JWKS fails before signature verification.
    import base64, json
    header = base64.urlsafe_b64encode(json.dumps({"alg": "ES256", "kid": "x"}).encode()).decode().rstrip("=")
    assert client.post("/auth/supabase", json={"access_token": header + ".e30.eA", "consent": True}).status_code == 503


def test_unsupported_algorithm_rejected(client, configured):
    token = jwt.encode({"sub": "s", "aud": "authenticated", "exp": int(time.time()) + 60}, "x" * 64, algorithm="HS384")
    assert client.post("/auth/supabase", json={"access_token": token, "consent": True}).status_code == 401


def test_invalid_profile_claims_rejected(client, configured):
    token = jwt.encode({"sub": "s", "aud": "authenticated", "exp": int(time.time()) + 60, "user_metadata": {"name": []}}, SECRET, algorithm="HS256")
    assert client.post("/auth/supabase", json={"access_token": token, "consent": True}).status_code == 401
