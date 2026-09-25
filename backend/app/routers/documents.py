from datetime import date

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import Response
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import audit, get_current_patient, get_owned_journey
from ..extraction import KNOWN_TEST_CODES, detect_language, extract_image, extract_values, normalize_value
from ..flags import compute_flags
from ..models import Document, DocumentBlob, Milestone, Observation, Patient
from ..schemas import DocumentConfirmRequest
from ..storage import read_upload, save_upload
from ..template_loader import load_template

router = APIRouter(tags=["documents"])

MAX_UPLOAD_BYTES = 10 * 1024 * 1024


def _report_bytes(doc: Document, db: Session) -> bytes:
    row = db.get(DocumentBlob, doc.id)
    if row is not None:
        return row.content
    # Pre-migration local uploads may be gone on a different serverless instance.
    try:
        return read_upload(doc.storage_path)
    except OSError:
        raise HTTPException(status_code=410, detail="Original report no longer available; please upload it again")


@router.get("/documents/{document_id}/original")
def original_document(document_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    doc = _get_owned_document(document_id, patient, db)
    if doc.content_type not in ("application/pdf", "image/png", "image/jpeg", "image/webp", "text/plain"):
        raise HTTPException(status_code=415, detail="Preview not available for this file type")
    return Response(content=_report_bytes(doc, db), media_type=doc.content_type,
                    headers={"Content-Disposition": "inline", "X-Content-Type-Options": "nosniff", "Cache-Control": "no-store"})



def _get_owned_document(document_id: str, patient: Patient, db: Session) -> Document:
    doc = db.get(Document, document_id)
    if doc is None:
        raise HTTPException(status_code=404, detail="Document not found")
    get_owned_journey(doc.journey_id, patient, db)  # 404 if it belongs to someone else
    return doc


@router.post("/journeys/{journey_id}/documents/upload", status_code=201)
def upload_document(
    journey_id: str,
    file: UploadFile = File(...),
    milestone_id: str | None = Form(default=None),
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    get_owned_journey(journey_id, patient, db)
    if milestone_id:
        m = db.get(Milestone, milestone_id)
        if m is None or m.journey_id != journey_id:
            raise HTTPException(status_code=400, detail="Milestone does not belong to this journey")
    data = file.file.read(MAX_UPLOAD_BYTES + 1)
    if not data:
        raise HTTPException(status_code=400, detail="Empty file")
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="File too large (max 10 MB)")
    doc = Document(
        journey_id=journey_id,
        milestone_id=milestone_id or None,
        filename=file.filename or "report",
        content_type=file.content_type or "application/octet-stream",
        status="uploaded",
    )
    db.add(doc)
    db.flush()
    db.add(DocumentBlob(document_id=doc.id, content=data))
    doc.storage_path = "db:" + doc.id
    audit(db, f"patient:{patient.id}", "upload_document", "document", doc.id, {"filename": doc.filename})
    db.commit()
    return {"document_id": doc.id, "status": doc.status, "filename": doc.filename}


@router.post("/documents/{document_id}/extract")
def extract_document(
    document_id: str,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    """Propose values from the uploaded report. Nothing is saved as an Observation
    yet - the mother reviews the 'Is this right?' card and confirms."""
    doc = _get_owned_document(document_id, patient, db)
    journey = get_owned_journey(doc.journey_id, patient, db)
    template = load_template(journey.template_id, journey.template_version)

    raw = _report_bytes(doc, db)
    if doc.filename.lower().endswith((".txt", ".md")) or doc.content_type.startswith("text/"):
        text = raw.decode("utf-8", errors="replace")
    elif doc.filename.lower().endswith(".pdf") or doc.content_type == "application/pdf":
        try:
            import io
            from pypdf import PdfReader
            reader = PdfReader(io.BytesIO(raw))
            if len(reader.pages) > 10:
                raise HTTPException(status_code=422, detail="More than 10 pages; split the report and review each part")
            text = "\n".join(page.extract_text() or "" for page in reader.pages)
        except ImportError:
            raise HTTPException(status_code=415, detail="PDF text reading needs the pypdf package")
        except HTTPException:
            raise
        except Exception:
            raise HTTPException(status_code=422, detail="PDF could not be read; review the original and upload a clearer report")
        if not text.strip():
            raise HTTPException(status_code=422, detail="No readable text in this PDF (scanned reports need a vision API key)")
    elif doc.content_type.startswith("image/") or doc.filename.lower().endswith((".jpg", ".jpeg", ".png", ".webp", ".heic")):
        text = None
    else:
        raise HTTPException(status_code=415, detail="Unsupported file type. Upload a photo, PDF or text report.")

    if text is None:
        ext = doc.filename.lower().rsplit(".", 1)[-1]
        mime = doc.content_type if doc.content_type.startswith("image/") else {"jpg": "image/jpeg", "jpeg": "image/jpeg", "webp": "image/webp", "heic": "image/heic"}.get(ext, "image/png")
        proposed, provider, doc.language = extract_image(raw, mime, template)
        if provider in ("no-key", "provider-error"):
            raise HTTPException(status_code=503, detail="Could not read this photo right now. Please try again, or upload a PDF.")
    else:
        doc.language = detect_language(text)
        proposed, provider = extract_values(text, doc.language, template)
    doc.status = "extracted"
    audit(db, f"patient:{patient.id}", "extract_document", "document", doc.id, {"provider": provider, "language": doc.language})
    db.commit()
    return {
        "document_id": doc.id,
        "filename": doc.filename,
        "language": doc.language,
        "provider": provider,
        "proposed": proposed,
        "message": None if proposed else "No tracked values found in this report. You can still confirm it against a milestone.",
    }


@router.post("/documents/{document_id}/confirm")
def confirm_document(
    document_id: str,
    body: DocumentConfirmRequest,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    """The mother (or doctor) approved the extracted values - now they become
    Observations on her timeline and the rules engine re-runs."""
    doc = _get_owned_document(document_id, patient, db)
    journey = get_owned_journey(doc.journey_id, patient, db)
    template = load_template(journey.template_id, journey.template_version)

    milestone_id = body.milestone_id or doc.milestone_id
    if milestone_id:
        m = db.get(Milestone, milestone_id)
        if m is None or m.journey_id != journey.id:
            raise HTTPException(status_code=400, detail="Milestone does not belong to this journey")

    if doc.status not in ("extracted", "confirmed"):
        raise HTTPException(status_code=409, detail="Extract the report and review the original before confirming")

    codes = [v.code for v in body.values]
    if len(codes) != len(set(codes)):
        raise HTTPException(status_code=422, detail="Duplicate test values; review the original report")
    _report_bytes(doc, db)  # never confirm a legacy report whose evidence is gone
    # Validate every value before deleting an earlier confirmation.
    normalized = []
    for v in body.values:
        if v.code not in KNOWN_TEST_CODES:
            raise HTTPException(status_code=400, detail=f"Unknown test code: {v.code}")
        try:
            normalized.append(normalize_value(v.code, v.value, v.unit))
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc))

    # Replace any values confirmed earlier from this document (re-confirm is safe).
    db.query(Observation).filter(Observation.document_id == doc.id).delete()

    saved = []
    for v, (value, unit) in zip(body.values, normalized):
        obs = Observation(
            journey_id=journey.id,
            milestone_id=milestone_id,
            document_id=doc.id,
            code=v.code,
            value=value,
            unit=unit,
            observed_on=v.observed_on or date.today(),
            source="extracted",
        )
        db.add(obs)
        db.flush()
        saved.append({"id": obs.id, "code": obs.code, "value": obs.value, "unit": obs.unit, "observed_on": obs.observed_on.isoformat()})

    doc.milestone_id = milestone_id
    doc.status = "confirmed"
    audit(db, f"patient:{patient.id}", "confirm_document", "document", doc.id, {"values": len(saved)})
    db.commit()
    return {
        "document_id": doc.id,
        "status": doc.status,
        "milestone_id": milestone_id,
        "observations": saved,
        "flags": compute_flags(db, journey.id, template),
    }
