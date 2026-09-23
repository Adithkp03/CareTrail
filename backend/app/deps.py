from datetime import datetime, timezone

from fastapi import Depends, Header, HTTPException
from sqlalchemy.orm import Session

from .database import get_db
from .models import AuthToken, Journey, Patient


def get_current_patient(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
) -> Patient:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    token_value = authorization.split(None, 1)[1].strip()
    token = db.get(AuthToken, token_value)
    if token is None:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    expires_at = token.expires_at
    if expires_at.tzinfo is None:  # SQLite returns naive datetimes
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at < datetime.now(timezone.utc):
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    patient = db.get(Patient, token.patient_id)
    if patient is None:
        raise HTTPException(status_code=401, detail="Unknown patient")
    return patient


def get_owned_journey(journey_id: str, patient: Patient, db: Session) -> Journey:
    journey = db.get(Journey, journey_id)
    if journey is None or journey.patient_id != patient.id:
        # 404, not 403: do not reveal that someone else's journey exists
        raise HTTPException(status_code=404, detail="Journey not found")
    return journey


def audit(db: Session, actor: str, action: str, entity: str, entity_id: str, detail: dict | None = None):
    from .models import AuditLog

    db.add(AuditLog(actor=actor, action=action, entity=entity, entity_id=entity_id, detail=detail or {}))
