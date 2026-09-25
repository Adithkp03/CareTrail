from datetime import date

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from .. import engine as journey_engine
from ..database import get_db
from ..deps import audit, get_current_patient, get_owned_journey
from ..flags import compute_flags
from ..models import Journey, Milestone, Patient, SignOff
from ..schemas import JourneyCreateRequest
from ..template_loader import load_template

router = APIRouter(tags=["journey"])


def milestone_payload(m: Milestone, ga_days: int, today: date, signoffs: list[SignOff], template_milestones: dict) -> dict:
    # Existing journeys store milestone rows. Read the current v1 wording/window too,
    # so an existing patient does not keep the old NT end of week 14.
    current = template_milestones.get(m.key, {})
    start, end = current.get("window", [m.window_start_weeks, m.window_end_weeks])
    status, overdue = journey_engine.milestone_status(
        ga_days, start, end, m.completed_at, m.scheduled_date, today
    )
    return {
        "id": m.id,
        "key": m.key,
        "title": m.title,
        "type": m.type,
        "window_weeks": [start, end],
        "required_tests": m.required_tests,
        "prep_notes": current.get("prep_notes", m.prep_notes),
        "status": status,
        "overdue": overdue,
        "completed_at": m.completed_at.isoformat() if m.completed_at else None,
        "scheduled_date": m.scheduled_date.isoformat() if m.scheduled_date else None,
        "signoff": (
            {
                "doctor_name": signoffs[-1].doctor_name,
                "verified_clinician": bool(signoffs[-1].clinician_id),
                "note": signoffs[-1].note,
                "signed_at": signoffs[-1].signed_at.isoformat(),
            }
            if signoffs
            else None
        ),
    }


def journey_payload(db: Session, journey: Journey, today: date | None = None) -> dict:
    today = today or date.today()
    template = load_template(journey.template_id, journey.template_version)
    ga_days = journey_engine.gestational_age_days(journey.lmp, journey.edd, today)
    weeks, days = divmod(ga_days, 7)

    milestone_ids = [m.id for m in journey.milestones]
    all_signoffs: dict[str, list[SignOff]] = {mid: [] for mid in milestone_ids}
    if milestone_ids:
        for s in db.query(SignOff).filter(SignOff.milestone_id.in_(milestone_ids)).order_by(SignOff.signed_at):
            all_signoffs.setdefault(s.milestone_id, []).append(s)

    template_milestones = {item["key"]: item for item in template["milestones"]}
    milestones = [milestone_payload(m, ga_days, today, all_signoffs.get(m.id, []), template_milestones) for m in journey.milestones]
    next_appointments = [
        {"key": m["key"], "title": m["title"], "scheduled_date": m["scheduled_date"]}
        for m in milestones
        if m["status"] == "upcoming" and m["scheduled_date"]
    ]
    next_appointments.sort(key=lambda a: a["scheduled_date"])

    return {
        "journey_id": journey.id,
        "template_id": journey.template_id,
        "template_version": journey.template_version,
        "patient": {
            "name": journey.patient.name,
            "language": journey.patient.language,
        },
        "gestational_age": {"days": ga_days, "weeks": weeks, "plus_days": days, "as_of": today.isoformat()},
        "lmp": journey.lmp.isoformat() if journey.lmp else None,
        "edd": (journey.edd or (journey_engine.edd_from_lmp(journey.lmp) if journey.lmp else None)).isoformat(),
        "summary": {
            "done": sum(1 for m in milestones if m["status"] == "done"),
            "now": sum(1 for m in milestones if m["status"] == "now"),
            "upcoming": sum(1 for m in milestones if m["status"] == "upcoming"),
            "next": sum(1 for m in milestones if m["status"] == "next"),
        },
        "next_appointment": next_appointments[0] if next_appointments else None,
        "milestones": milestones,
        "flags": compute_flags(db, journey.id, template),
        "danger_signs": template.get("danger_signs", []),
    }


@router.post("/journeys", status_code=201)
def create_journey(
    body: JourneyCreateRequest,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    try:
        template = load_template(body.template_id, "v1")
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    journey = Journey(
        patient_id=patient.id,
        template_id=template["template_id"],
        template_version=template["version"],
        lmp=body.lmp,
        edd=body.edd,
    )
    db.add(journey)
    db.flush()
    for row in journey_engine.build_milestone_rows(template):
        db.add(Milestone(journey_id=journey.id, **row))
    audit(db, f"patient:{patient.id}", "create_journey", "journey", journey.id, {"template": template["template_id"]})
    db.commit()
    return journey_payload(db, journey)


@router.get("/journeys")
def list_journeys(patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    journeys = db.query(Journey).filter(Journey.patient_id == patient.id).order_by(Journey.created_at.desc()).all()
    today = date.today()
    out = []
    for j in journeys:
        ga_days = journey_engine.gestational_age_days(j.lmp, j.edd, today)
        weeks, days = divmod(ga_days, 7)
        out.append(
            {
                "journey_id": j.id,
                "template_id": j.template_id,
                "gestational_age": {"weeks": weeks, "plus_days": days},
                "created_at": j.created_at.isoformat(),
            }
        )
    return {"journeys": out}


@router.get("/journey/{journey_id}")
def get_journey(
    journey_id: str,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    journey = get_owned_journey(journey_id, patient, db)
    return journey_payload(db, journey)
