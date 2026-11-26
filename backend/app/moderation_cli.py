"""Provision a community review-queue moderator token. Run inside the backend
environment:  python -m app.moderation_cli create "Name"
Prints the token once; only its presence in the table grants access. There is no
list or re-show command - a lost token is revoked and re-created."""
import hashlib
import secrets
import sys

from .database import SessionLocal
from .models import CommunityModerator
from .routers.moderation import purge_expired_reports


def main() -> int:
    if len(sys.argv) == 3 and sys.argv[1] == "create":
        name = sys.argv[2].strip()[:120]
        if not name:
            print("Name is required", file=sys.stderr)
            return 2
        token = "ctmod-" + secrets.token_urlsafe(24)
        with SessionLocal() as db:
            db.add(CommunityModerator(token_hash=hashlib.sha256(token.encode()).hexdigest(), name=name))
            db.commit()
        print(f"Moderator '{name}' token (shown once): {token}")
        return 0
    if len(sys.argv) == 3 and sys.argv[1] == "revoke":
        with SessionLocal() as db:
            n = db.query(CommunityModerator).filter_by(name=sys.argv[2].strip()[:120]).delete()
            db.commit()
        print(f"Revoked {n} token(s)")
        return 0
    if len(sys.argv) == 2 and sys.argv[1] == "purge":
        with SessionLocal() as db:
            n = purge_expired_reports(db)
        print(f"Purged {n} expired report(s)")
        return 0
    print("usage: python -m app.moderation_cli create|revoke <name> | purge", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
