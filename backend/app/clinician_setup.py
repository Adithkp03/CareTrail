"""One-time clinician password setup. Only operators mint links; never expose mint over HTTP."""
import hashlib
import secrets
from datetime import datetime, timedelta, timezone

from sqlalchemy import update
from sqlalchemy.orm import Session

from .models import Clinician, ClinicianSetupToken, ClinicianToken
from .security import hash_password

SETUP_HOURS = 24


def digest(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def mint(db: Session, clinician: Clinician) -> str:
    if not clinician.active:
        raise ValueError("Inactive clinician")
    # Invalidate older unredeemed setup links whenever a new one is minted.
    db.query(ClinicianSetupToken).filter_by(clinician_id=clinician.id).delete()
    raw = secrets.token_urlsafe(32)
    db.add(ClinicianSetupToken(token_hash=digest(raw), clinician_id=clinician.id,
                               expires_at=datetime.now(timezone.utc) + timedelta(hours=SETUP_HOURS)))
    db.commit()
    return raw


def redeem(db: Session, raw: str, password: str) -> bool:
    if not 12 <= len(password) <= 128:
        raise ValueError("Password must be 12-128 characters")
    now = datetime.now(timezone.utc)
    # Conditional database update is atomic across concurrent requests. It consumes
    # the link before setting the password in the same transaction.
    changed = db.execute(update(ClinicianSetupToken).where(
        ClinicianSetupToken.token_hash == digest(raw), ClinicianSetupToken.used_at.is_(None),
        ClinicianSetupToken.expires_at > now,
    ).values(used_at=now))
    if changed.rowcount != 1:
        db.rollback()
        return False
    row = db.get(ClinicianSetupToken, digest(raw))
    clinician = db.get(Clinician, row.clinician_id)
    if not clinician or not clinician.active:
        db.rollback()
        return False
    clinician.password_hash = hash_password(password)
    db.query(ClinicianToken).filter_by(clinician_id=clinician.id).delete(synchronize_session=False)
    db.commit()
    return True
