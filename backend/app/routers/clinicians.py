"""Clinician-only review, with explicit patient grants per journey."""
from datetime import timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ..clinicians import authorized_journey, current_clinician
from ..clinician_setup import redeem
from .. import ratelimit
from ..database import get_db
from ..deps import audit, get_current_patient, get_owned_journey
from ..flags import compute_flags
from ..models import CareInstruction, utcnow, Clinician, ClinicianToken, Journey, JourneyClinicianGrant, Milestone, Observation, Patient, SignOff
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
def setup_password(body: SetupPassword, request: Request, db: Session = Depends(get_db)):
    ratelimit.check(db, request, "login_failed")
    if not 12 <= len(body.password) <= 128:
        raise HTTPException(status_code=400, detail="Password must be 12-128 characters")
    if not redeem(db, body.token, body.password):
        ratelimit.record(db, request, "login_failed")
        raise HTTPException(status_code=400, detail="Setup link is invalid, used or expired")
    return {"status": "password_set"}


@router.post("/clinician/login")
def login(body: ClinicianLogin, request: Request, db: Session = Depends(get_db)):
    ratelimit.check(db, request, "login_failed", "clin:" + body.email)
    clinician = db.query(Clinician).filter(Clinician.email == body.email.strip().lower()).first()
    if not clinician or not clinician.active or not verify_password(body.password, clinician.password_hash):
        ratelimit.record(db, request, "login_failed", "clin:" + body.email)
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
    return {"patient": journey.patient.name, "journey_id": journey.id, "flags": flags, "tft_clinician_note": load_template(journey.template_id, journey.template_version).get("tft_clinician_note", "")}


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


class InstructionCreate(BaseModel):
    note: str = Field(min_length=1, max_length=4000)


def instruction_time(value):
    return value.replace(tzinfo=timezone.utc).isoformat() if value.tzinfo is None else value.isoformat()


def instruction_rows(db, journey_id):
    rows = db.query(CareInstruction, Clinician.name).join(Clinician, Clinician.id == CareInstruction.clinician_id).filter(CareInstruction.journey_id == journey_id).order_by(CareInstruction.created_at.desc(), CareInstruction.id.desc()).all()
    return [{"id": row.id, "note": row.note, "doctor_name": name, "created_at": instruction_time(row.created_at), "acknowledged_at": instruction_time(row.acknowledged_at) if row.acknowledged_at else None} for row, name in rows]


@router.post("/clinician/journeys/{journey_id}/instructions", status_code=201)
def create_instruction(journey_id: str, body: InstructionCreate, clinician: Clinician = Depends(current_clinician), db: Session = Depends(get_db)):
    journey = authorized_journey(db, journey_id, clinician)
    note = body.note.strip()
    if not note:
        raise HTTPException(status_code=422, detail="Write an instruction first")
    row = CareInstruction(journey_id=journey.id, clinician_id=clinician.id, note=note)
    db.add(row)
    db.flush()
    audit(db, f"clinician:{clinician.id}", "create_instruction", "care_instruction", row.id, {"journey_id": journey.id})
    db.commit()
    return {"id": row.id}


@router.get("/clinician/journeys/{journey_id}/instructions")
def clinician_instructions(journey_id: str, clinician: Clinician = Depends(current_clinician), db: Session = Depends(get_db)):
    authorized_journey(db, journey_id, clinician)
    return {"items": instruction_rows(db, journey_id)}


@router.get("/journey/{journey_id}/instructions")
def patient_instructions(journey_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    get_owned_journey(journey_id, patient, db)
    return {"items": instruction_rows(db, journey_id)}


@router.post("/instructions/{instruction_id}/acknowledge")
def acknowledge_instruction(instruction_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    row = db.get(CareInstruction, instruction_id)
    if row is None:
        raise HTTPException(status_code=404, detail="Instruction not found")
    get_owned_journey(row.journey_id, patient, db)
    if row.acknowledged_at is None:
        row.acknowledged_at = utcnow()
        audit(db, f"patient:{patient.id}", "acknowledge_instruction", "care_instruction", row.id)
        db.commit()
    return {"id": row.id, "acknowledged_at": instruction_time(row.acknowledged_at)}


@router.get("/clinician/patients")
def clinician_patients(clinician: Clinician = Depends(current_clinician), db: Session = Depends(get_db)):
    """Everyone who granted this clinician access: who needs review first."""
    from datetime import date

    from .. import engine as journey_engine

    rows = (db.query(Journey).join(JourneyClinicianGrant, JourneyClinicianGrant.journey_id == Journey.id)
            .filter(JourneyClinicianGrant.clinician_id == clinician.id).all())
    out = []
    for j in rows:
        template = load_template(j.template_id, j.template_version)
        flags = compute_flags(db, j.id, template)
        pending = [f for f in flags if not f.get("signed_off")]
        unread = db.query(CareInstruction).filter(CareInstruction.journey_id == j.id, CareInstruction.acknowledged_at.is_(None)).count()
        ga = journey_engine.gestational_age_days(j.lmp, j.edd, date.today())
        edd = j.edd or (journey_engine.edd_from_lmp(j.lmp) if j.lmp else None)
        out.append({"journey_id": j.id, "patient": j.patient.name, "weeks": ga // 7, "plus_days": ga % 7,
                    "edd": edd.isoformat() if edd else None, "pending_reviews": len(pending),
                    "instructions_waiting": unread})
    out.sort(key=lambda r: (-r["pending_reviews"], r["patient"].lower()))
    return {"items": out, "pending_total": sum(r["pending_reviews"] for r in out)}
