"""General-help assistant chat (Sprint 6). General information and app help only -
no medical advice. History is stored so the conversation survives reopening the
app, and the patient can wipe it at any time."""

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from .. import assistant
from ..database import get_db
from ..deps import audit, get_current_patient
from ..models import AssistantMessage, Journey, Patient

router = APIRouter(tags=["assistant"])

CHAT_LIMIT_PER_HOUR = 60


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=1000)
    lang: str = ""


def _row(m: AssistantMessage) -> dict:
    at = m.created_at
    return {
        "id": m.id,
        "role": m.role,
        "text": m.text,
        "source": m.source,
        "urgent": m.urgent,
        "suggest_doctor": m.suggest_doctor,
        "created_at": (at.replace(tzinfo=timezone.utc) if at.tzinfo is None else at).isoformat(),
    }


@router.post("/assistant/chat", status_code=201)
def chat(body: ChatRequest, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    since = datetime.now(timezone.utc) - timedelta(hours=1)
    recent = db.query(AssistantMessage).filter(AssistantMessage.patient_id == patient.id, AssistantMessage.created_at >= since).count()
    if recent >= CHAT_LIMIT_PER_HOUR:
        raise HTTPException(status_code=429, detail="Too many messages. Please wait a little and try again.")
    message = body.message.strip()
    language = body.lang or assistant.detect_question_language(message)
    journey = db.query(Journey).filter(Journey.patient_id == patient.id).order_by(Journey.created_at.desc()).first()
    result = assistant.answer_chat(db, journey, message, language)
    user_row = AssistantMessage(patient_id=patient.id, role="user", text=message, language=language)
    db.add(user_row)
    answer_row = AssistantMessage(
        patient_id=patient.id, role="assistant", text=result["answer"], language=language,
        source=result["source"], urgent=result["urgent"], suggest_doctor=result["suggest_doctor"],
    )
    db.add(answer_row)
    db.flush()
    audit(db, f"patient:{patient.id}", "assistant_chat", "patient", patient.id, {"source": result["source"], "urgent": result["urgent"]})
    db.commit()
    return {"message_id": answer_row.id, **result}


@router.get("/assistant/history")
def history(patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    rows = (
        db.query(AssistantMessage)
        .filter(AssistantMessage.patient_id == patient.id)
        .order_by(AssistantMessage.created_at.desc(), AssistantMessage.id.desc())
        .limit(100)
        .all()
    )
    return {"items": [_row(m) for m in reversed(rows)]}


@router.delete("/assistant/history")
def clear_history(patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    deleted = db.query(AssistantMessage).filter(AssistantMessage.patient_id == patient.id).delete()
    audit(db, f"patient:{patient.id}", "assistant_clear", "patient", patient.id, {"deleted": deleted})
    db.commit()
    return {"deleted": deleted}
