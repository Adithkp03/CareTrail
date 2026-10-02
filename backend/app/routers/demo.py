import hmac
import os

from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import AuthToken, Patient
from ..seed import DEMO_PHONE, seed_anjali

router = APIRouter(prefix="/demo", tags=["demo"])


def _may_reset(db: Session, authorization: str | None, demo_key: str | None) -> bool:
    expected = os.getenv("DEMO_RESET_KEY", "")
    if expected and demo_key and hmac.compare_digest(demo_key, expected):
        return True
    demo = db.query(Patient).filter(Patient.phone == DEMO_PHONE).first()
    if demo is None:
        return True  # first-time creation of the synthetic account only
    if authorization and authorization.lower().startswith("bearer "):
        tok = db.get(AuthToken, authorization.split(None, 1)[1].strip())
        return tok is not None and tok.patient_id == demo.id
    return False


@router.post("/seed")
def seed_demo(
    db: Session = Depends(get_db),
    authorization: str | None = Header(default=None),
    x_demo_key: str | None = Header(default=None),
):
    """Create/reset the synthetic demo mother Anjali. Creating her is open; resetting
    an existing demo needs the demo user's own session or the X-Demo-Key header
    (env DEMO_RESET_KEY). It can only ever touch the demo account."""
    if not _may_reset(db, authorization, x_demo_key):
        raise HTTPException(status_code=403, detail="Demo reset requires the demo session")
    return seed_anjali(db)
