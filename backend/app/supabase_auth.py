"""Verify Supabase identities without querying patient tables from the client."""
from functools import lru_cache
from urllib.parse import urlparse

import jwt

from .config import SUPABASE_JWT_SECRET, SUPABASE_URL


@lru_cache(maxsize=4)
def _jwks_client(project_url: str):
    parsed = urlparse(project_url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise RuntimeError("SUPABASE_URL must be the trusted HTTPS project origin")
    return jwt.PyJWKClient(f"{project_url}/auth/v1/.well-known/jwks.json", timeout=5)


def verify_access_token(access_token: str) -> dict:
    """Return a verified subject. Reject unconfigured algorithms and missing expiry."""
    if not SUPABASE_JWT_SECRET and not SUPABASE_URL:
        raise RuntimeError("Supabase auth is not configured")
    algorithm = jwt.get_unverified_header(access_token).get("alg")
    if algorithm == "HS256":
        if not SUPABASE_JWT_SECRET:
            raise RuntimeError("Legacy Supabase JWT verification is not configured")
        key = SUPABASE_JWT_SECRET
    elif algorithm in ("ES256", "RS256"):
        if not SUPABASE_URL:
            raise RuntimeError("Supabase project URL is not configured")
        # Neither the token's jku nor its issuer selects a network destination.
        key = _jwks_client(SUPABASE_URL).get_signing_key_from_jwt(access_token).key
    else:
        raise ValueError("Unsupported Supabase signing algorithm")
    claims = jwt.decode(
        access_token, key, algorithms=[algorithm], audience="authenticated",
        issuer=f"{SUPABASE_URL}/auth/v1" if SUPABASE_URL else None,
        options={"require": ["sub", "exp", "aud"]},
    )
    sub = claims.get("sub")
    if not isinstance(sub, str) or not sub.strip():
        raise ValueError("token has no subject")
    meta = claims.get("user_metadata") or {}
    if not isinstance(meta, dict) or any(not isinstance(claims.get(k, ""), str) for k in ("email", "phone")):
        raise ValueError("Invalid identity claims")
    if any(not isinstance(meta.get(k, ""), str) for k in ("name", "full_name")):
        raise ValueError("Invalid profile claims")
    return {
        "sub": sub,
        "email": claims.get("email") or "",
        "phone": (claims.get("phone") or "").strip(),
        "name": (meta.get("name") or meta.get("full_name") or "").strip(),
    }
