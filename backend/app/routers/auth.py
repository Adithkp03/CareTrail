import secrets
from datetime import datetime, timezone

import jwt
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_current_patient
from ..models import AuthToken, Patient
from ..schemas import LoginRequest, SignupRequest
from ..security import hash_password, new_token, token_expiry, verify_password
from ..supabase_auth import verify_access_token

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


class SupabaseExchangeRequest(BaseModel):
    access_token: str
    consent: bool = False
    language: str = "en"


@router.post("/supabase")
def supabase_exchange(body: SupabaseExchangeRequest, db: Session = Depends(get_db)):
    """Bridge Supabase Auth into CareTrail sessions. The frontend signs the user
    in with Supabase (email magic link / OTP), then trades the Supabase access
    token for a CareTrail token. First sign-in provisions the patient row."""
    try:
        ident = verify_access_token(body.access_token)
    except RuntimeError:
        raise HTTPException(status_code=501, detail="Supabase auth is not configured on this server")
    except (jwt.PyJWTError, ValueError):
        raise HTTPException(status_code=401, detail="Invalid Supabase token")

    patient = db.query(Patient).filter(Patient.supabase_id == ident["sub"]).first()
    if patient is None:
        if not body.consent:
            raise HTTPException(status_code=422, detail="Consent is required to create an account")
        # Phone comes from a Supabase phone claim when present; otherwise a
        # placeholder keeps the unique column satisfied (user can add one later).
        phone = ident["phone"] or ("sb-" + ident["sub"].replace("-", "")[:29])
        name = ident["name"] or (ident["email"].split("@")[0] if ident["email"] else "Patient")
        patient = Patient(
            name=name[:120],
            phone=phone,
            email=ident["email"] or None,
            supabase_id=ident["sub"],
            language=body.language if body.language in ("en", "ml", "hi", "ta", "te", "kn", "bn", "mr") else "en",
            # Unguessable password: legacy login stays closed for this account.
            password_hash=hash_password(secrets.token_hex(16)),
            consent_at=datetime.now(timezone.utc),
        )
        db.add(patient)
        db.commit()
    return _issue_token(db, patient)
