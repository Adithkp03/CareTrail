"""Download your data, or delete your account and everything under it."""
import json

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import audit, get_current_patient
from ..models import (AiCallTrace, AuditLog, AuthToken, CareInstruction, Clinician, Document, DocumentBlob, Journey,
                      JourneyClinicianGrant, Milestone, Observation, Patient, ReminderState, SignOff)

router = APIRouter(tags=["account"])


def _iso(v):
    return v.isoformat() if v is not None else None


@router.get("/me/export")
def export_my_data(patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    journeys = []
    for j in db.query(Journey).filter(Journey.patient_id == patient.id).order_by(Journey.created_at):
        docs = db.query(Document).filter(Document.journey_id == j.id).all()
        doctor_names = {c.id: c.name for c in db.query(Clinician).filter(
            Clinician.id.in_([i.clinician_id for i in db.query(CareInstruction).filter(CareInstruction.journey_id == j.id)] or [""]))}
        journeys.append({
            "id": j.id, "template": j.template_id, "lmp": _iso(j.lmp), "edd": _iso(j.edd), "created_at": _iso(j.created_at),
            "milestones": [{"key": m.key, "title": m.title, "completed_at": _iso(m.completed_at), "scheduled_date": _iso(m.scheduled_date)}
                           for m in db.query(Milestone).filter(Milestone.journey_id == j.id).order_by(Milestone.sort_order)],
            "results": [{"code": o.code, "value": o.value, "unit": o.unit, "observed_on": _iso(o.observed_on), "source": o.source,
                         "report_id": o.document_id}
                        for o in db.query(Observation).filter(Observation.journey_id == j.id).order_by(Observation.observed_on)],
            "reports": [{"id": d.id, "filename": d.filename, "status": d.status, "uploaded_at": _iso(d.uploaded_at),
                         "sha256": getattr(d, "content_hash", None), "read_values": getattr(d, "extracted_values", None)}
                        for d in docs],
            "doctor_signoffs": [{"doctor": s.doctor_name, "verified_clinician": bool(s.clinician_id), "note": s.note, "signed_at": _iso(s.signed_at)}
                                for s in db.query(SignOff).filter(SignOff.journey_id == j.id)],
            "doctor_instructions": [{"doctor": doctor_names.get(i.clinician_id), "note": i.note, "created_at": _iso(i.created_at),
                                     "acknowledged_at": _iso(i.acknowledged_at)}
                                    for i in db.query(CareInstruction).filter(CareInstruction.journey_id == j.id)],
        })
    data = {
        "exported_for": {"name": patient.name, "phone": patient.phone, "email": patient.email, "language": patient.language,
                         "consent_at": _iso(patient.consent_at), "created_at": _iso(patient.created_at)},
        "journeys": journeys,
        "note": "Original report files are not included in this download. Open them from the report on your timeline.",
    }
    audit(db, f"patient:{patient.id}", "export_data", "patient", patient.id, {})
    db.commit()
    return Response(content=json.dumps(data, indent=2), media_type="application/json",
                    headers={"Content-Disposition": 'attachment; filename="caretrail-my-data.json"', "Cache-Control": "no-store"})


class DeleteAccountRequest(BaseModel):
    confirm: str


@router.delete("/me")
def delete_my_account(body: DeleteAccountRequest, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    if patient.is_demo:
        raise HTTPException(status_code=403, detail="The demo account cannot be deleted")
    if body.confirm != "DELETE":
        raise HTTPException(status_code=422, detail="Type DELETE to confirm")
    pid = patient.id
    jids = [j.id for j in db.query(Journey).filter(Journey.patient_id == pid)]
    if jids:
        dids = [d.id for d in db.query(Document).filter(Document.journey_id.in_(jids))]
        db.query(CareInstruction).filter(CareInstruction.journey_id.in_(jids)).delete(synchronize_session=False)
        db.query(JourneyClinicianGrant).filter(JourneyClinicianGrant.journey_id.in_(jids)).delete(synchronize_session=False)
        db.query(SignOff).filter(SignOff.journey_id.in_(jids)).delete(synchronize_session=False)
        db.query(Observation).filter(Observation.journey_id.in_(jids)).delete(synchronize_session=False)
        if dids:
            db.query(DocumentBlob).filter(DocumentBlob.document_id.in_(dids)).delete(synchronize_session=False)
        db.query(Document).filter(Document.journey_id.in_(jids)).delete(synchronize_session=False)
        db.query(Milestone).filter(Milestone.journey_id.in_(jids)).delete(synchronize_session=False)
        db.query(AuditLog).filter(AuditLog.entity == "journey", AuditLog.entity_id.in_(jids)).delete(synchronize_session=False)
        db.query(Journey).filter(Journey.patient_id == pid).delete(synchronize_session=False)
    db.query(ReminderState).filter(ReminderState.patient_id == pid).delete(synchronize_session=False)
    db.query(AiCallTrace).filter(AiCallTrace.patient_id == pid).delete(synchronize_session=False)
    db.query(AuditLog).filter(AuditLog.actor == f"patient:{pid}").delete(synchronize_session=False)
    db.query(AuthToken).filter(AuthToken.patient_id == pid).delete(synchronize_session=False)
    db.query(Patient).filter(Patient.id == pid).delete(synchronize_session=False)
    audit(db, "system", "delete_account", "patient", pid, {})
    db.commit()
    return {"deleted": True}
