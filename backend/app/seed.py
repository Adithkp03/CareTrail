"""Demo seed: synthetic mother Anjali, 27, at 22 weeks, matching the hackathon example:
first consultation and baseline bloods done, second-trimester review now,
anomaly scan upcoming, next consultation already scheduled.
Demo only - real patients sign up and create their own journeys."""

from datetime import date, timedelta

from sqlalchemy.orm import Session

from . import engine as journey_engine
from .models import Document, Journey, Milestone, Observation, Patient, SignOff
from .security import hash_password
from .template_loader import load_template

DEMO_PHONE = "9000000001"
DEMO_PASSWORD = "demo1234"

_GA_DAYS = 22 * 7  # 22 weeks 0 days


def seed_anjali(db: Session, today: date | None = None) -> dict:
    today = today or date.today()
    lmp = today - timedelta(days=_GA_DAYS)

    patient = db.query(Patient).filter(Patient.phone == DEMO_PHONE).first()
    if patient is None:
        patient = Patient(
            name="Anjali",
            phone=DEMO_PHONE,
            language="ml",
            password_hash=hash_password(DEMO_PASSWORD),
            is_demo=True,
        )
        db.add(patient)
        db.flush()
    else:
        # Reset any previous demo journey so re-seeding restores the starting state.
        # Children must be deleted explicitly: the relationships do not cascade, so a
        # bare db.delete(journey) nullifies milestone.journey_id and violates NOT NULL.
        journey_ids = [
            j.id for j in db.query(Journey).filter(Journey.patient_id == patient.id).all()
        ]
        if journey_ids:
            milestone_ids = [
                m.id for m in db.query(Milestone).filter(Milestone.journey_id.in_(journey_ids)).all()
            ]
            for model, column in [
                (SignOff, SignOff.journey_id),
                (Observation, Observation.journey_id),
                (Document, Document.journey_id),
            ]:
                db.query(model).filter(column.in_(journey_ids)).delete(synchronize_session=False)
            db.query(Milestone).filter(Milestone.id.in_(milestone_ids)).delete(synchronize_session=False)
            db.query(Journey).filter(Journey.id.in_(journey_ids)).delete(synchronize_session=False)
        db.flush()

    template = load_template("antenatal", "v1")
    journey = Journey(
        patient_id=patient.id,
        template_id=template["template_id"],
        template_version=template["version"],
        lmp=lmp,
        edd=journey_engine.edd_from_lmp(lmp),
    )
    db.add(journey)
    db.flush()

    done_dates = {
        "first_consultation": lmp + timedelta(days=8 * 7 + 2),   # at 8w2d
        "baseline_bloods": lmp + timedelta(days=8 * 7 + 4),      # at 8w4d
        "nt_scan": lmp + timedelta(days=12 * 7 + 1),             # at 12w1d
    }
    scheduled = {
        "anomaly_scan": lmp + timedelta(days=22 * 7 + 4),        # 22w4d, a few days out
        "consultation_24w": lmp + timedelta(days=24 * 7),        # 24w0d
    }
    milestones: dict[str, Milestone] = {}
    for row in journey_engine.build_milestone_rows(template):
        m = Milestone(
            journey_id=journey.id,
            completed_at=done_dates.get(row["key"]),
            scheduled_date=scheduled.get(row["key"]),
            **row,
        )
        db.add(m)
        milestones[row["key"]] = m
    db.flush()

    bloods = milestones["baseline_bloods"]
    bloods_on = done_dates["baseline_bloods"]
    for code, value, unit in [
        ("hb", 11.6, "g/dL"),
        ("bp_sys", 110, "mmHg"),
        ("bp_dia", 70, "mmHg"),
        ("glucose_fasting", 88, "mg/dL"),
    ]:
        db.add(
            Observation(
                journey_id=journey.id,
                milestone_id=bloods.id,
                code=code,
                value=value,
                unit=unit,
                observed_on=bloods_on,
                source="seed",
            )
        )
    db.commit()
    return {"patient_id": patient.id, "journey_id": journey.id, "phone": DEMO_PHONE, "password": DEMO_PASSWORD}


if __name__ == "__main__":
    from .database import Base, SessionLocal, engine as db_engine

    Base.metadata.create_all(db_engine)
    with SessionLocal() as db:
        ids = seed_anjali(db)
    print("Seeded demo journey:", ids)
