"""Phase 5: next-up preparation card and the doctor's pre-consult brief."""

from datetime import date

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from .. import engine as journey_engine
from ..database import get_db
from ..deps import get_current_patient, get_owned_journey
from ..explanations import MILESTONE_EXPLANATIONS_EN
from ..flags import compute_flags
from ..models import Document, Observation, Patient
from ..template_loader import load_template

router = APIRouter(tags=["prep"])

FASTING_MILESTONES = {"ogtt": "Come fasting: 8-10 hours, water is allowed. The test takes about 2 hours."}

QUESTIONS_BY_TYPE = {
    "visit": ["Is my blood pressure okay?", "Is the baby's growth on track?", "What should I watch for before the next visit?"],
    "scan": ["Is everything developing normally?", "Where is the placenta?", "When is my next scan?"],
    "test": ["What do my results mean?", "Do I need any change in diet or tablets?", "When should I repeat this test?"],
    "vaccination": ["Are there side effects to expect?", "Does the baby get protection right away?"],
    "review": ["Is my birth plan on track?", "When exactly should I go to the hospital?"],
}


@router.get("/journey/{journey_id}/next-up")
def next_up(journey_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    """What the next visit is for, what to bring, fasting rules, questions worth asking."""
    journey = get_owned_journey(journey_id, patient, db)
    today = date.today()
    ga_days = journey_engine.gestational_age_days(journey.lmp, journey.edd, today)

    candidates = []
    for m in journey.milestones:
        status, overdue = journey_engine.milestone_status(ga_days, m.window_start_weeks, m.window_end_weeks, m.completed_at, m.scheduled_date, today)
        if status in ("now", "upcoming"):
            candidates.append((0 if status == "now" else 1, m.scheduled_date or date(2099, 1, 1), m.window_end_weeks, m))
    if not candidates:
        return {"milestone": None, "message": "No upcoming check-ups on the timeline."}
    m = sorted(candidates, key=lambda c: (c[0], c[1], c[2]))[0][3]

    return {
        "milestone": {
            "id": m.id,
            "key": m.key,
            "title": m.title,
            "type": m.type,
            "window_weeks": [m.window_start_weeks, m.window_end_weeks],
            "scheduled_date": m.scheduled_date.isoformat() if m.scheduled_date else None,
        },
        "purpose": MILESTONE_EXPLANATIONS_EN.get(m.key) or m.prep_notes or m.title,
        "what_to_bring": m.prep_notes or "Your previous reports and this app.",
        "fasting": FASTING_MILESTONES.get(m.key),
        "questions": QUESTIONS_BY_TYPE.get(m.type, QUESTIONS_BY_TYPE["visit"]),
    }


@router.get("/journey/{journey_id}/brief")
def preconsult_brief(journey_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    """One screen for the doctor before the consultation: GA, open flags, latest
    values, next appointment, recent reports."""
    journey = get_owned_journey(journey_id, patient, db)
    template = load_template(journey.template_id, journey.template_version)
    today = date.today()
    ga_days = journey_engine.gestational_age_days(journey.lmp, journey.edd, today)
    weeks, days = divmod(ga_days, 7)

    flags = compute_flags(db, journey.id, template)
    latest: dict[str, Observation] = {}
    for obs in db.query(Observation).filter(Observation.journey_id == journey.id).order_by(Observation.observed_on.desc(), Observation.id.desc()):
        latest.setdefault(obs.code, obs)

    next_appt = next(
        (
            {"title": m.title, "scheduled_date": m.scheduled_date.isoformat()}
            for m in sorted(journey.milestones, key=lambda m: m.scheduled_date or date(2099, 1, 1))
            if m.completed_at is None and m.scheduled_date and m.scheduled_date >= today
        ),
        None,
    )
    documents = db.query(Document).filter(Document.journey_id == journey.id).order_by(Document.uploaded_at.desc()).limit(5).all()

    return {
        "patient": {"name": journey.patient.name, "language": journey.patient.language},
        "gestational_age": {"weeks": weeks, "plus_days": days, "as_of": today.isoformat()},
        "edd": (journey.edd or journey_engine.edd_from_lmp(journey.lmp)).isoformat(),
        "open_flags": [f for f in flags if not f.get("signed_off")],
        "signed_flags": [f for f in flags if f.get("signed_off")],
        "latest_values": [
            {"code": o.code, "value": o.value, "unit": o.unit, "observed_on": o.observed_on.isoformat()} for o in latest.values()
        ],
        "next_appointment": next_appt,
        "recent_reports": [{"filename": d.filename, "status": d.status, "uploaded_at": d.uploaded_at.isoformat()} for d in documents],
    }
