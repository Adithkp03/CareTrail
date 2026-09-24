from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_current_patient
from ..models import AuthToken, Patient
from ..schemas import LoginRequest, SignupRequest
from ..security import hash_password, new_token, token_expiry, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])


def _issue_token(db: Session, patient: Patient) -> dict:
    token = AuthToken(token=new_token(), patient_id=patient.id, expires_at=token_expiry())
    db.add(token)
    db.commit()
    return {"token": token.token, "patient_id": patient.id}


@router.post("/signup", status_code=201)
def signup(body: SignupRequest, db: Session = Depends(get_db)):
    if db.query(Patient).filter(Patient.phone == body.phone).first():
        raise HTTPException(status_code=409, detail="An account with this phone already exists")
    if not body.consent:
        raise HTTPException(status_code=422, detail="Consent is required to create an account")
    patient = Patient(
        name=body.name.strip(),
        phone=body.phone.strip(),
        language=body.language,
        password_hash=hash_password(body.password),
        consent_at=datetime.now(timezone.utc),
    )
    db.add(patient)
    db.commit()
    return _issue_token(db, patient)


@router.post("/login")
def login(body: LoginRequest, db: Session = Depends(get_db)):
    patient = db.query(Patient).filter(Patient.phone == body.phone.strip()).first()
    if patient is None or not verify_password(body.password, patient.password_hash):
        raise HTTPException(status_code=401, detail="Wrong phone or password")
    return _issue_token(db, patient)


@router.get("/me")
def me(patient: Patient = Depends(get_current_patient)):
    return {
        "patient_id": patient.id,
        "name": patient.name,
        "phone": patient.phone,
        "language": patient.language,
    }
