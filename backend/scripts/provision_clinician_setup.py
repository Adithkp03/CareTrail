"""Operator-only, no HTTP mint route. Link is printed once, never logged or stored.

Example: cd backend && python -m scripts.provision_clinician_setup \
  --email doctor@example.org --name 'Dr Example' --web-origin https://caretrail-web.vercel.app
The operator hands the resulting link to the account owner over a trusted channel;
the owner forwards it to the verified clinician. Treat the URL as a secret.
"""
import argparse
import secrets
from urllib.parse import quote

from app.clinician_setup import mint
from app.database import Base, SessionLocal, engine
from app.models import Clinician, ClinicianSetupToken
from app.security import hash_password


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--email", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--web-origin", required=True)
    args = parser.parse_args()
    origin = args.web_origin.rstrip("/")
    if not origin.startswith("https://"):
        raise SystemExit("HTTPS web origin required")
    Base.metadata.create_all(engine)
    with SessionLocal() as db:
        clinician = db.query(Clinician).filter_by(email=args.email.strip().lower()).first()
        if clinician:
            if not clinician.active or clinician.name != args.name.strip():
                raise SystemExit("Existing clinician account does not match; manual review needed")
            if not db.query(ClinicianSetupToken).filter_by(clinician_id=clinician.id).filter(ClinicianSetupToken.used_at.is_(None)).first():
                raise SystemExit("Account is not pending setup; no reset link minted")
        else:
            # A random unusable password hash avoids placing a shared password in circulation.
            clinician = Clinician(email=args.email.strip().lower(), name=args.name.strip(),
                                  password_hash=hash_password(secrets.token_urlsafe(48)))
            db.add(clinician)
            db.flush()
        raw = mint(db, clinician)
    print(f"{origin}/clinician/setup#token={quote(raw)}")
    print("Expires after 24 hours; a new setup link invalidates the old one. No patient access exists until granted.")


if __name__ == "__main__":
    main()
