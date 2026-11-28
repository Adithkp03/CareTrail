"""Clinician-side EHR documents: upload, list and view a patient's records."""

import hashlib

from fastapi import APIRouter, Depends, HTTPException, UploadFile
from fastapi.responses import Response
from sqlalchemy.orm import Session

from ..deps import audit
from ..clinicians import authorized_journey, current_clinician
from ..database import get_db
from ..models import Clinician, Document, DocumentBlob

router = APIRouter(prefix="/clinician", tags=["clinician-documents"])

MAX_DOCUMENT_BYTES = 10 * 1024 * 1024  # matches the patient report limit


def _serialize(d: Document) -> dict:
    return {
        "document_id": d.id,
        "filename": d.filename,
        "content_type": d.content_type,
        "source": d.source or "patient",
        "status": d.status,
        "uploaded_at": d.uploaded_at.isoformat(),
    }


@router.post("/journeys/{journey_id}/documents/upload")
async def upload_document(
    journey_id: str,
    clinician: Clinician = Depends(current_clinician),
    file: UploadFile | None = None,
    db: Session = Depends(get_db),
):
    journey = authorized_journey(db, journey_id, clinician)
    if file is None or not file.filename:
        raise HTTPException(status_code=400, detail="Attach a file")
    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail="File is empty")
    if len(content) > MAX_DOCUMENT_BYTES:
        raise HTTPException(status_code=413, detail="File is too large (10 MB limit)")
    content_hash = hashlib.sha256(content).hexdigest()
    existing = (
        db.query(Document)
        .filter(Document.journey_id == journey.id, Document.content_hash == content_hash)
        .first()
    )
    if existing:
        return {"duplicate_of": existing.id, "filename": existing.filename}
    document = Document(
        journey_id=journey.id,
        milestone_id=None,
        filename=file.filename,
        content_type=file.content_type or "application/octet-stream",
        content_hash=content_hash,
        source="clinician",
    )
    db.add(document)
    db.flush()
    db.add(DocumentBlob(document_id=document.id, content=content))
    audit(db, f"clinician:{clinician.id}", "upload_document", "document", document.id, {"filename": document.filename, "journey_id": journey.id})
    db.commit()
    return _serialize(document)


@router.get("/journeys/{journey_id}/documents")
def list_documents(
    journey_id: str,
    clinician: Clinician = Depends(current_clinician),
    db: Session = Depends(get_db),
):
    journey = authorized_journey(db, journey_id, clinician)
    rows = (
        db.query(Document)
        .filter(Document.journey_id == journey.id)
        .order_by(Document.uploaded_at.desc())
        .all()
    )
    return {"items": [_serialize(d) for d in rows]}


@router.get("/documents/{document_id}/original")
def document_original(
    document_id: str,
    clinician: Clinician = Depends(current_clinician),
    db: Session = Depends(get_db),
):
    document = db.get(Document, document_id)
    if not document:
        raise HTTPException(status_code=404, detail="Document not found")
    authorized_journey(db, document.journey_id, clinician)  # raises 404 without a grant
    blob = db.get(DocumentBlob, document.id)
    if blob is None:
        raise HTTPException(status_code=404, detail="Original file not stored")
    content_type = document.content_type or "application/octet-stream"
    if content_type not in ("application/pdf", "image/png", "image/jpeg", "image/webp", "text/plain"):
        content_type = "application/octet-stream"
    return Response(content=bytes(blob.content), media_type=content_type)
