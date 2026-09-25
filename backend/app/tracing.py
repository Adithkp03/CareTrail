"""Privacy-safe provider metadata. Never persist prompt, output or exception text."""

import time
from contextlib import contextmanager

from sqlalchemy.orm import Session

from .models import AiCallTrace


@contextmanager
def trace(db: Session, provider: str, kind: str, prompt: str, patient_id: str | None = None):
    """Usage: with trace(db, "sarvam", "chat", prompt) as t: ... t.finish(output)"""
    start = time.perf_counter()
    # prompt is accepted for compatibility but deliberately never stored.
    row = AiCallTrace(provider=provider, kind=kind, patient_id=patient_id, prompt="", output="")

    class _T:
        def finish(self, output: str, status: str = "ok"):
            row.status = status
            row.latency_ms = int((time.perf_counter() - start) * 1000)
            db.add(row)
            db.commit()

    try:
        yield _T()
    except Exception as exc:
        row.status = "error"
        row.latency_ms = int((time.perf_counter() - start) * 1000)
        db.add(row)
        db.commit()
        raise
