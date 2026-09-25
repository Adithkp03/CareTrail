"""Operator-only CLI. Verify the clinician out of band before using this script.

Example: cd backend && python -m scripts.provision_clinician --email doctor@example.org --name 'Dr Example'
Password is requested by getpass, never printed or accepted as a CLI argument.
"""
import argparse
from getpass import getpass

from app.database import Base, SessionLocal, engine
from app.models import Clinician
from app.security import hash_password


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--email", required=True)
    parser.add_argument("--name", required=True)
    args = parser.parse_args()
    password = getpass("New clinician password (12+ chars): ")
    if len(password) < 12:
        raise SystemExit("Password must be at least 12 characters")
    Base.metadata.create_all(engine)
    with SessionLocal() as db:
        if db.query(Clinician).filter_by(email=args.email.strip().lower()).first():
            raise SystemExit("Account exists; no change made")
        db.add(Clinician(email=args.email.strip().lower(), name=args.name.strip(), password_hash=hash_password(password)))
        db.commit()
    print("Clinician account created. No patient data is accessible until the patient grants a journey.")


if __name__ == "__main__":
    main()
