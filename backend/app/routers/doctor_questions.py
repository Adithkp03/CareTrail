"""Patient -> clinician questions (Sprint 6).

The patient writes and sends each question explicitly. Questions go only to
clinicians she has granted journey access to (existing grant flow); a clinician
answers from the clinician web view. This is not an emergency channel and no
response time is promised - the app copy says so next to the composer."""

from datetime import timezone

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ..clinicians import authorized_journey, current_clinician
from ..database import get_db
from ..deps import audit, get_current_patient, get_owned_journey
from ..models import Clinician, DoctorQuestion, JourneyClinicianGrant, Patient, utcnow

router = APIRouter(tags=["doctor-questions"])

MAX_OPEN = 20


class QuestionCreate(BaseModel):
    question: str = Field(min_length=1, max_length=2000)


class AnswerCreate(BaseModel):
    answer: str = Field(min_length=1, max_length=4000)


def _ts(value):
    return (value.replace(tzinfo=timezone.utc) if value.tzinfo is None else value).isoformat() if value else None


def _row(q: DoctorQuestion) -> dict:
    return {
        "id": q.id,
        "journey_id": q.journey_id,
        "question": q.question,
        "created_at": _ts(q.created_at),
        "answer": q.answer,
        "answered_at": _ts(q.answered_at),
        "answerer_name": q.answerer_name,
        "answered": q.answer is not None,
    }


def _granted_clinicians(db: Session, journey_id: str) -> list[Clinician]:
    return (
        db.query(Clinician)
        .join(JourneyClinicianGrant, JourneyClinicianGrant.clinician_id == Clinician.id)
        .filter(JourneyClinicianGrant.journey_id == journey_id, Clinician.active.is_(True))
        .all()
    )


@router.post("/journeys/{journey_id}/doctor-questions", status_code=201)
def ask_doctor(journey_id: str, body: QuestionCreate, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    get_owned_journey(journey_id, patient, db)
    clinicians = _granted_clinicians(db, journey_id)
    if not clinicians:
        raise HTTPException(status_code=409, detail="Link your doctor first (Doctor section) so your question has somewhere to go.")
    open_count = db.query(DoctorQuestion).filter(DoctorQuestion.journey_id == journey_id, DoctorQuestion.answer.is_(None)).count()
    if open_count >= MAX_OPEN:
        raise HTTPException(status_code=429, detail="Too many unanswered questions. Please wait for your doctor to reply.")
    text = body.question.strip()
    row = DoctorQuestion(journey_id=journey_id, patient_id=patient.id, question=text)
    db.add(row)
    db.flush()
    audit(db, f"patient:{patient.id}", "doctor_question", "doctor_question", row.id, {"journey_id": journey_id})
    db.commit()
    return _row(row)


@router.get("/journeys/{journey_id}/doctor-questions")
def list_questions(journey_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    get_owned_journey(journey_id, patient, db)
    rows = db.query(DoctorQuestion).filter(DoctorQuestion.journey_id == journey_id).order_by(DoctorQuestion.created_at.desc(), DoctorQuestion.id.desc()).all()
    return {
        "items": [_row(q) for q in rows],
        "linked_doctors": [{"name": c.name} for c in _granted_clinicians(db, journey_id)],
    }


@router.get("/clinician/journeys/{journey_id}/questions")
def clinician_questions(journey_id: str, clinician: Clinician = Depends(current_clinician), db: Session = Depends(get_db)):
    authorized_journey(db, journey_id, clinician)
    rows = db.query(DoctorQuestion).filter(DoctorQuestion.journey_id == journey_id).order_by(DoctorQuestion.created_at.desc(), DoctorQuestion.id.desc()).all()
    return {"items": [_row(q) for q in rows]}


@router.post("/clinician/questions/{question_id}/answer")
def answer_question(question_id: str, body: AnswerCreate, clinician: Clinician = Depends(current_clinician), db: Session = Depends(get_db)):
    row = db.get(DoctorQuestion, question_id)
    if row is None:
        raise HTTPException(status_code=404, detail="Question not found")
    authorized_journey(db, row.journey_id, clinician)
    row.answer = body.answer.strip()
    row.answered_at = utcnow()
    row.answered_by = clinician.id
    row.answerer_name = clinician.name
    audit(db, f"clinician:{clinician.id}", "answer_question", "doctor_question", row.id, {"journey_id": row.journey_id})
    db.commit()
    return _row(row)
