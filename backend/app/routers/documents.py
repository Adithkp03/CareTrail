from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import audit, get_current_patient, get_owned_journey
from ..models import Document, Milestone, Patient
from ..schemas import DocumentCreateRequest

router = APIRouter(tags=["documents"])


@router.post("/documents", status_code=201)
def register_document(
    body: DocumentCreateRequest,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    get_owned_journey(body.journey_id, patient, db)
    if body.milestone_id is not None:
        m = db.get(Milestone, body.milestone_id)
        if m is None or m.journey_id != body.journey_id:
            from fastapi import HTTPException

            raise HTTPException(status_code=400, detail="Milestone does not belong to this journey")
    doc = Document(
        journey_id=body.journey_id,
        milestone_id=body.milestone_id,
        filename=body.filename,
        content_type=body.content_type,
        storage_path=body.storage_path,
        language=body.language,
        status="stored",  # value extraction is phase 3
    )
    db.add(doc)
    db.flush()
    audit(db, f"patient:{patient.id}", "upload_document", "document", doc.id, {"filename": doc.filename})
    db.commit()
    return {"document_id": doc.id, "status": doc.status}
