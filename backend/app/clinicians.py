"""Separate clinician identity and patient-authorized journey access."""
from datetime import datetime, timezone

from fastapi import Depends, Header, HTTPException
from sqlalchemy.orm import Session

from .database import get_db
from .models import Clinician, ClinicianToken, Journey, JourneyClinicianGrant


def current_clinician(authorization: str | None = Header(default=None), db: Session = Depends(get_db)) -> Clinician:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Clinician login required")
    row = db.get(ClinicianToken, authorization.split(None, 1)[1].strip())
    if row is None:
        raise HTTPException(status_code=401, detail="Invalid clinician session")
    expiry = row.expires_at
    if expiry.tzinfo is None:
        expiry = expiry.replace(tzinfo=timezone.utc)
    if expiry < datetime.now(timezone.utc):
        raise HTTPException(status_code=401, detail="Clinician session expired")
    clinician = db.get(Clinician, row.clinician_id)
    if clinician is None or not clinician.active:
        raise HTTPException(status_code=403, detail="Clinician access disabled")
    return clinician


def authorized_journey(db: Session, journey_id: str, clinician: Clinician) -> Journey:
    journey = db.get(Journey, journey_id)
    grant = db.query(JourneyClinicianGrant).filter_by(journey_id=journey_id, clinician_id=clinician.id).first()
    if journey is None or grant is None:
        raise HTTPException(status_code=404, detail="Journey not found")
    return journey
