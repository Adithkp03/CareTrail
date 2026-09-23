from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..seed import seed_anjali

router = APIRouter(prefix="/demo", tags=["demo"])


@router.post("/seed")
def seed_demo(db: Session = Depends(get_db)):
    """Create/reset the synthetic demo mother Anjali. Used by the frontend's demo-reset
    button and by tests. No auth: it can only ever touch the demo account."""
    return seed_anjali(db)
