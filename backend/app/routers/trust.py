from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_current_patient
from ..models import AiCallTrace, Patient

router = APIRouter(tags=["trust"])


@router.get("/ai-traces")
def list_ai_traces(
    limit: int = 20,
    patient: Patient = Depends(get_current_patient),
    db: Session = Depends(get_db),
):
    """The judges' trace view: every AI provider call with prompt, output, latency."""
    rows = db.query(AiCallTrace).order_by(AiCallTrace.created_at.desc()).limit(min(limit, 100)).all()
    return {
        "traces": [
            {
                "id": r.id,
                "provider": r.provider,
                "kind": r.kind,
                "prompt": r.prompt,
                "output": r.output,
                "status": r.status,
                "latency_ms": r.latency_ms,
                "at": r.created_at.isoformat(),
            }
            for r in rows
        ]
    }
