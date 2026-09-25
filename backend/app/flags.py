"""Rules-based risk flags. Thresholds come from the doctor-approved pathway template.
An LLM never decides a flag - it only explains one later."""

from datetime import date

from sqlalchemy.orm import Session

from .models import Observation, SignOff


def compute_flags(db: Session, journey_id: str, template: dict) -> list[dict]:
    thresholds = template.get("thresholds", {})
    observations = (
        db.query(Observation)
        .filter(Observation.journey_id == journey_id)
        .order_by(Observation.observed_on.desc(), Observation.id.desc())
        .all()
    )
    latest: dict[str, Observation] = {}
    for obs in observations:  # first row per code is the newest
        latest.setdefault(obs.code, obs)

    signoffs = db.query(SignOff).filter(SignOff.journey_id == journey_id, SignOff.observation_id.isnot(None)).all()
    signed: dict[str, SignOff] = {}
    for so in signoffs:
        signed.setdefault(so.observation_id, so)  # first = oldest; keep latest below
        signed[so.observation_id] = so

    flags = []
    for code, obs in latest.items():
        rule = thresholds.get(code)
        if rule is None:
            continue
        breached = ("min" in rule and obs.value < rule["min"]) or ("max" in rule and obs.value >= rule["max"])
        if not breached:
            continue
        flags.append(
            {
                "code": code,
                "label": rule.get("label", code),
                "value": obs.value,
                "unit": obs.unit,
                "observed_on": obs.observed_on.isoformat(),
                "observation_id": obs.id,
                "threshold": {k: rule[k] for k in ("min", "max") if k in rule},
                "message": rule.get("message", "Value outside the expected range."),
                "severity": "review",
                "signed_off": (
                    {
                        "doctor_name": signed[obs.id].doctor_name,
                        "verified_clinician": bool(signed[obs.id].clinician_id),
                        "note": signed[obs.id].note,
                        "signed_at": signed[obs.id].signed_at.isoformat(),
                    }
                    if obs.id in signed
                    else None
                ),
            }
        )
    return flags
