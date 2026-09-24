from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import Response
from sqlalchemy.orm import Session

from .. import voice
from ..answers import answer_question, detect_question_language
from ..database import get_db
from ..deps import audit, get_current_patient, get_owned_journey
from ..explanations import explanation_audio, milestone_explanation, result_explanation
from ..models import Journey, Milestone, Patient
from ..schemas import AskTextRequest
from ..template_loader import load_template

router = APIRouter(tags=["voice"])


def _latest_journey(patient: Patient, db: Session) -> Journey:
    journey = db.query(Journey).filter(Journey.patient_id == patient.id).order_by(Journey.created_at.desc()).first()
    if journey is None:
        raise HTTPException(status_code=404, detail="No journey yet")
    return journey


@router.get("/milestones/{milestone_id}/explanation")
def get_milestone_explanation(
    milestone_id: str,
    lang: str = "en",
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    m = db.get(Milestone, milestone_id)
    if m is None:
        raise HTTPException(status_code=404, detail="Milestone not found")
    journey = get_owned_journey(m.journey_id, patient, db)
    return milestone_explanation(db, m.key, m.title, m.prep_notes, journey.template_version, lang)


@router.get("/milestones/{milestone_id}/explanation/audio")
def get_milestone_explanation_audio(
    milestone_id: str,
    lang: str = "en",
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    m = db.get(Milestone, milestone_id)
    if m is None:
        raise HTTPException(status_code=404, detail="Milestone not found")
    get_owned_journey(m.journey_id, patient, db)
    milestone_explanation(db, m.key, m.title, m.prep_notes, m.journey.template_version, lang)  # ensure cached
    audio = explanation_audio(db, m.key, lang)
    if audio is None:
        raise HTTPException(status_code=503, detail="Audio needs SARVAM_API_KEY (Bulbul text-to-speech)")
    return Response(content=audio, media_type="audio/mpeg")


@router.get("/observations/{code}/explanation")
def get_result_explanation(
    code: str,
    label: str = "",
    lang: str = "en",
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    _latest_journey(patient, db)  # 404 if the patient has no journey at all
    return result_explanation(db, code, label or code, "v1", lang)


@router.post("/ask")
def ask_text(
    body: AskTextRequest,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    journey = _latest_journey(patient, db)
    template = load_template(journey.template_id, journey.template_version)
    language = body.lang or detect_question_language(body.question)
    result = answer_question(db, journey, body.question, language, template)
    audit(db, f"patient:{patient.id}", "ask_text", "journey", journey.id, {"source": result["source"], "urgent": result["urgent"]})
    db.commit()
    return {"question": body.question, "language": language, **result}


@router.post("/ask/voice")
def ask_voice(
    file: UploadFile = File(...),
    lang: str = Form(default=""),
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    audio = file.file.read()
    if not audio:
        raise HTTPException(status_code=400, detail="Empty audio")
    transcript = voice.stt(audio, file.filename or "voice.webm", lang or "ml")
    if transcript is None:
        raise HTTPException(status_code=503, detail="Voice questions need SARVAM_API_KEY (Saarika speech-to-text)")
    journey = _latest_journey(patient, db)
    template = load_template(journey.template_id, journey.template_version)
    language = lang or detect_question_language(transcript)
    result = answer_question(db, journey, transcript, language, template)
    spoken = voice.tts(result["answer"], language)
    audit(db, f"patient:{patient.id}", "ask_voice", "journey", journey.id, {"source": result["source"], "urgent": result["urgent"]})
    db.commit()
    import base64

    return {
        "transcript": transcript,
        "language": language,
        **result,
        "audio_base64": base64.b64encode(spoken).decode() if spoken else None,
    }
