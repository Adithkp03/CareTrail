"""Opt-in structured local-record backup. Never stores sessions, files or generic caches."""
import json
from typing import Any, Literal

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, Field
from sqlalchemy import update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_current_patient, get_owned_journey
from ..models import LocalRecordBackup, Patient, utcnow

router = APIRouter(tags=["local-record-backup"])
MODULES = {"health", "registration", "rh", "danger", "birth", "growth", "vaccines", "bp", "sugar", "high-risk", "follow-ups", "mood", "birth-draft"}


class BackupWrite(BaseModel):
    expected_revision: int = Field(ge=0)
    operation_id: str = Field(min_length=16, max_length=64, pattern=r"^[a-zA-Z0-9-]+$")
    schema_version: Literal[1] = 1
    # Explicit per-operation choice, including sensitive modules.
    consent: Literal[True]
    deleted: bool = False
    data: Any = None


def check_module(module):
    if module not in MODULES:
        raise HTTPException(422, "Unsupported structured-record module")


def view(row):
    return {"module": row.module, "revision": row.revision, "schema_version": row.schema_version,
            "deleted": row.deleted, "data": row.payload if not row.deleted else None,
            "updated_at": row.updated_at.isoformat(), "operation_id": row.operation_id}


@router.get("/journeys/{journey_id}/local-backup")
def read_backups(journey_id: str, response: Response, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    get_owned_journey(journey_id, patient, db)
    response.headers["Cache-Control"] = "no-store"
    rows = db.query(LocalRecordBackup).filter_by(journey_id=journey_id).order_by(LocalRecordBackup.module).all()
    return {"journey_id": journey_id, "records": [view(row) for row in rows]}


@router.put("/journeys/{journey_id}/local-backup/{module}")
def write_backup(journey_id: str, module: str, body: BackupWrite, response: Response,
                 patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    get_owned_journey(journey_id, patient, db)
    check_module(module)
    if patient.is_demo:
        raise HTTPException(403, "Cloud backup is unavailable for the shared demo. Use a private account")
    response.headers["Cache-Control"] = "no-store"
    if not body.deleted and not isinstance(body.data, (dict, list)):
        raise HTTPException(422, "A structured object or list is required")
    list_modules = {"danger", "growth", "bp", "sugar", "mood"}
    if not body.deleted and not isinstance(body.data, list if module in list_modules else dict):
        raise HTTPException(422, "Record shape does not match the selected module")
    payload = None if body.deleted else body.data
    def forbidden(value):
        if isinstance(value, dict):
            if any(str(k).lower() in {"token", "password", "authorization", "access_token", "refresh_token", "mcp_images", "cookie", "api_key"} for k in value):
                return True
            return any(forbidden(v) for v in value.values())
        if isinstance(value, list):
            return any(forbidden(v) for v in value)
        return False
    if forbidden(payload):
        raise HTTPException(422, "Sessions, credentials and file paths are not backup records")
    if len(json.dumps(payload, ensure_ascii=False).encode("utf-8")) > 65536:
        raise HTTPException(413, "This module exceeds the 64 KiB backup limit")
    key = f"{journey_id}:{module}"
    row = db.get(LocalRecordBackup, key)
    if row and row.operation_id == body.operation_id:
        if row.payload != payload or row.deleted != body.deleted or row.schema_version != body.schema_version:
            raise HTTPException(409, "Operation ID already used with different content")
        return view(row)
    now = utcnow()
    if row is None:
        if body.expected_revision != 0:
            raise HTTPException(409, "Backup changed. Preview the server copy before retrying")
        row = LocalRecordBackup(id=key, journey_id=journey_id, module=module, revision=1,
                                schema_version=1, payload=payload, deleted=body.deleted,
                                operation_id=body.operation_id, updated_at=now)
        db.add(row)
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
            raise HTTPException(409, "Backup changed. Preview the server copy before retrying")
    else:
        result = db.execute(update(LocalRecordBackup).where(LocalRecordBackup.id == key,
                            LocalRecordBackup.revision == body.expected_revision).values(
            revision=body.expected_revision + 1, schema_version=1, payload=payload,
            deleted=body.deleted, operation_id=body.operation_id, updated_at=now))
        if result.rowcount != 1:
            db.rollback()
            raise HTTPException(409, "Backup changed. Preview the server copy before retrying")
        db.commit()
        db.expire_all()
        row = db.get(LocalRecordBackup, key)
    return view(row)
