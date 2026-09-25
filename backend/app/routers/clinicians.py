"""Clinician-only review, with explicit patient grants per journey."""
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..clinicians import authorized_journey, current_clinician
from ..clinician_setup import redeem
from ..database import get_db
from ..deps import audit, get_current_patient, get_owned_journey
from ..flags import compute_flags
from ..models import Clinician, ClinicianToken, JourneyClinicianGrant, Milestone, Observation, Patient, SignOff
from ..schemas import SignOffCreateRequest
from ..security import new_token, token_expiry, verify_password
from ..template_loader import load_template

router = APIRouter(tags=["clinicians"])


class ClinicianLogin(BaseModel):
    email: str
    password: str


class GrantRequest(BaseModel):
    clinician_email: str


class SetupPassword(BaseModel):
    token: str
    password: str


@router.post("/clinician/setup-password")
def setup_password(body: SetupPassword, db: Session = Depends(get_db)):
    if not 12 <= len(body.password) <= 128:
        raise HTTPException(status_code=400, detail="Password must be 12-128 characters")
    if not redeem(db, body.token, body.password):
        raise HTTPException(status_code=400, detail="Setup link is invalid, used or expired")
    return {"status": "password_set"}


@router.post("/clinician/login")
def login(body: ClinicianLogin, db: Session = Depends(get_db)):
    clinician = db.query(Clinician).filter(Clinician.email == body.email.strip().lower()).first()
    if not clinician or not clinician.active or not verify_password(body.password, clinician.password_hash):
        raise HTTPException(status_code=401, detail="Invalid clinician login")
    token = ClinicianToken(token=new_token(), clinician_id=clinician.id, expires_at=token_expiry())
    db.add(token)
    db.commit()
    return {"token": token.token, "name": clinician.name}


@router.post("/journeys/{journey_id}/clinicians", status_code=201)
def grant_access(journey_id: str, body: GrantRequest, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    get_owned_journey(journey_id, patient, db)
    clinician = db.query(Clinician).filter(Clinician.email == body.clinician_email.strip().lower(), Clinician.active.is_(True)).first()
    if clinician is None:
        raise HTTPException(status_code=404, detail="Verified clinician account not found")
    row = db.query(JourneyClinicianGrant).filter_by(journey_id=journey_id, clinician_id=clinician.id).first()
    if row is None:
        db.add(JourneyClinicianGrant(journey_id=journey_id, clinician_id=clinician.id))
        audit(db, f"patient:{patient.id}", "grant_clinician", "journey", journey_id, {"clinician_id": clinician.id})
        db.commit()
    return {"clinician_name": clinician.name, "journey_id": journey_id}


@router.delete("/journeys/{journey_id}/clinicians/{clinician_id}")
def revoke_access(journey_id: str, clinician_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    get_owned_journey(journey_id, patient, db)
    db.query(JourneyClinicianGrant).filter_by(journey_id=journey_id, clinician_id=clinician_id).delete()
    audit(db, f"patient:{patient.id}", "revoke_clinician", "journey", journey_id)
    db.commit()
    return {"revoked": True}


@router.get("/clinician/journeys/{journey_id}/review")
def review(journey_id: str, clinician: Clinician = Depends(current_clinician), db: Session = Depends(get_db)):
    journey = authorized_journey(db, journey_id, clinician)
    flags = compute_flags(db, journey.id, load_template(journey.template_id, journey.template_version))
    return {"patient": journey.patient.name, "journey_id": journey.id, "flags": flags}


@router.post("/clinician/signoffs", status_code=201)
def signoff(body: SignOffCreateRequest, clinician: Clinician = Depends(current_clinician), db: Session = Depends(get_db)):
    journey = authorized_journey(db, body.journey_id, clinician)
    if not body.milestone_id and not body.observation_id:
        raise HTTPException(status_code=400, detail="Select a milestone or observation")
    if body.milestone_id:
        m = db.get(Milestone, body.milestone_id)
        if not m or m.journey_id != journey.id:
            raise HTTPException(status_code=404, detail="Milestone not found")
    if body.observation_id:
        obs = db.get(Observation, body.observation_id)
        if not obs or obs.journey_id != journey.id:
            raise HTTPException(status_code=404, detail="Observation not found")
    row = SignOff(journey_id=journey.id, milestone_id=body.milestone_id, observation_id=body.observation_id, doctor_name=clinician.name, clinician_id=clinician.id, note=body.note)
    db.add(row)
    db.flush()
    audit(db, f"clinician:{clinician.id}", "signoff", "signoff", row.id, {"journey_id": journey.id})
    db.commit()
    return {"signoff_id": row.id, "doctor_name": clinician.name}
