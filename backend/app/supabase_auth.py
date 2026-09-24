"""Verify Supabase Auth access tokens (HS256, project JWT secret) and extract
the user identity. Phone OTP on Supabase needs a paid SMS provider, so the
expected flow is email magic link / email OTP (free tier)."""

import jwt

from .config import SUPABASE_JWT_SECRET


def verify_access_token(access_token: str) -> dict:
    """Return {"sub", "email", "phone", "name"} or raise ValueError."""
    if not SUPABASE_JWT_SECRET:
        raise RuntimeError("Supabase auth is not configured")
    claims = jwt.decode(
        access_token,
        SUPABASE_JWT_SECRET,
        algorithms=["HS256"],
        audience="authenticated",
    )
    sub = claims.get("sub")
    if not sub:
        raise ValueError("token has no subject")
    meta = claims.get("user_metadata") or {}
    return {
        "sub": sub,
        "email": claims.get("email") or "",
        "phone": (claims.get("phone") or "").strip(),
        "name": (meta.get("name") or "").strip(),
    }
