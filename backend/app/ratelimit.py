"""Best-effort brute-force throttle backed by the existing audit_logs table
(durable across serverless instances, no new table). Only failures count."""
from datetime import datetime, timedelta, timezone

from fastapi import HTTPException, Request
from sqlalchemy.orm import Session

from .models import AuditLog

WINDOW_MINUTES = 15
LIMITS = {"login_failed": (8, 40), "signup": (10, 30)}  # (per identifier, per IP)
DEMO_PHONE = "9000000001"  # never lock the shared demo account out


def client_ip(request: Request) -> str:
    fwd = request.headers.get("x-forwarded-for", "")
    return (fwd.split(",")[0].strip() or (request.client.host if request.client else "unknown"))[:40]


def _count(db: Session, action: str, key: str) -> int:
    since = datetime.now(timezone.utc) - timedelta(minutes=WINDOW_MINUTES)
    return db.query(AuditLog).filter(AuditLog.action == action, AuditLog.entity_id == key, AuditLog.at >= since).count()


def check(db: Session, request: Request, action: str, identifier: str = "") -> None:
    per_id, per_ip = LIMITS[action]
    ip = "ip:" + client_ip(request)
    ident = "id:" + identifier.strip().lower()[:34]
    blocked = _count(db, action, ip) >= per_ip
    if identifier and identifier.strip() != DEMO_PHONE:
        blocked = blocked or _count(db, action, ident) >= per_id
    if blocked:
        raise HTTPException(status_code=429, detail="Too many attempts. Please wait 15 minutes and try again.",
                            headers={"Retry-After": str(WINDOW_MINUTES * 60)})


def record(db: Session, request: Request, action: str, identifier: str = "") -> None:
    db.add(AuditLog(actor="anonymous", action=action, entity="auth", entity_id="ip:" + client_ip(request)))
    if identifier:
        db.add(AuditLog(actor="anonymous", action=action, entity="auth", entity_id="id:" + identifier.strip().lower()[:34]))
    db.commit()
