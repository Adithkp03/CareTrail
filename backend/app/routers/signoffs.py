"""The legacy patient-session sign-off is deliberately disabled."""
from fastapi import APIRouter, HTTPException

router = APIRouter(tags=["signoffs"])


@router.post("/signoffs", status_code=403)
def legacy_signoff_disabled():
    raise HTTPException(status_code=403, detail="Use a verified clinician account for sign-off")
