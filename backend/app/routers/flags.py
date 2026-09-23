from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_current_patient, get_owned_journey
from ..flags import compute_flags
from ..models import Patient
from ..template_loader import load_template

router = APIRouter(tags=["flags"])


@router.get("/flags")
def get_flags(journey_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    journey = get_owned_journey(journey_id, patient, db)
    template = load_template(journey.template_id, journey.template_version)
    return {"journey_id": journey.id, "flags": compute_flags(db, journey.id, template)}
