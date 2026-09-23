from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import audit, get_current_patient, get_owned_journey
from ..models import Milestone, Observation, Patient, SignOff
from ..schemas import SignOffCreateRequest
from .journeys import journey_payload

router = APIRouter(tags=["signoffs"])


@router.post("/signoffs", status_code=201)
def create_signoff(
    body: SignOffCreateRequest,
    x_doctor_name: str | None = Header(default=None),
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    """A doctor signs off a milestone or a flagged value; the badge lands on the mother's timeline.

    Hackathon scope: the doctor acts through the patient's session with an X-Doctor-Name
    header. A separate doctor login with its own role lands in the hardening phase.
    """
    if not x_doctor_name:
        raise HTTPException(status_code=400, detail="X-Doctor-Name header is required")
    journey = get_owned_journey(body.journey_id, patient, db)
    if body.milestone_id is not None:
        m = db.get(Milestone, body.milestone_id)
        if m is None or m.journey_id != journey.id:
            raise HTTPException(status_code=400, detail="Milestone does not belong to this journey")
    if body.observation_id is not None:
        o = db.get(Observation, body.observation_id)
        if o is None or o.journey_id != journey.id:
            raise HTTPException(status_code=400, detail="Observation does not belong to this journey")
    signoff = SignOff(
        journey_id=journey.id,
        milestone_id=body.milestone_id,
        observation_id=body.observation_id,
        doctor_name=x_doctor_name.strip(),
        note=body.note,
    )
    db.add(signoff)
    db.flush()
    audit(db, f"doctor:{x_doctor_name.strip()}", "signoff", "signoff", signoff.id, {"journey_id": journey.id})
    db.commit()
    return {"signoff_id": signoff.id, "journey": journey_payload(db, journey)}
