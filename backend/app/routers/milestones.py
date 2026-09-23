from datetime import date

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import audit, get_current_patient, get_owned_journey
from ..models import Document, Milestone, Observation, Patient, SignOff
from ..schemas import MilestoneCompleteRequest, MilestoneScheduleRequest
from .journeys import journey_payload, milestone_payload
from .. import engine as journey_engine

router = APIRouter(tags=["milestones"])


def _get_owned_milestone(milestone_id: str, patient: Patient, db: Session) -> Milestone:
    m = db.get(Milestone, milestone_id)
    if m is None:
        raise HTTPException(status_code=404, detail="Milestone not found")
    get_owned_journey(m.journey_id, patient, db)  # raises 404 if not the owner's
    return m


@router.get("/milestones/{milestone_id}")
def get_milestone(milestone_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    m = _get_owned_milestone(milestone_id, patient, db)
    today = date.today()
    ga_days = journey_engine.gestational_age_days(m.journey.lmp, m.journey.edd, today)
    signoffs = db.query(SignOff).filter(SignOff.milestone_id == m.id).order_by(SignOff.signed_at).all()
    documents = db.query(Document).filter(Document.milestone_id == m.id).all()
    observations = db.query(Observation).filter(Observation.milestone_id == m.id).all()
    payload = milestone_payload(m, ga_days, today, signoffs)
    payload["documents"] = [
        {"id": d.id, "filename": d.filename, "status": d.status, "language": d.language} for d in documents
    ]
    payload["observations"] = [
        {"id": o.id, "code": o.code, "value": o.value, "unit": o.unit, "observed_on": o.observed_on.isoformat()}
        for o in observations
    ]
    return payload


@router.post("/milestones/{milestone_id}/complete")
def complete_milestone(
    milestone_id: str,
    body: MilestoneCompleteRequest,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    m = _get_owned_milestone(milestone_id, patient, db)
    m.completed_at = body.completed_at or date.today()
    m.scheduled_date = None
    audit(db, f"patient:{patient.id}", "complete_milestone", "milestone", m.id, {"notes": body.notes})
    db.commit()
    return journey_payload(db, m.journey)


@router.post("/milestones/{milestone_id}/schedule")
def schedule_milestone(
    milestone_id: str,
    body: MilestoneScheduleRequest,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    m = _get_owned_milestone(milestone_id, patient, db)
    if m.completed_at is not None:
        raise HTTPException(status_code=409, detail="Milestone is already completed")
    m.scheduled_date = body.scheduled_date
    audit(db, f"patient:{patient.id}", "schedule_milestone", "milestone", m.id, {"date": body.scheduled_date.isoformat()})
    db.commit()
    return journey_payload(db, m.journey)
