"""Opt-in patient community room. Text only, pseudonymous, post-moderated via the
team review queue. No health records, no direct messages, no 24/7 monitoring -
the client always shows the not-monitored-for-emergencies notice."""
import os
import re
import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import audit, get_current_patient
from ..models import (CommunityBlock, CommunityMember, CommunityMessage,
                      CommunityReport, Patient, utcnow)

def community_available():
    """Hard launch gate: until the team turns COMMUNITY_LAUNCH on in the deployed
    environment, every community endpoint 404s for every patient, including old
    clients and direct API calls. Hidden UI is not the gate; this is."""
    if os.environ.get("COMMUNITY_LAUNCH", "").strip().lower() not in {"1", "true", "yes"}:
        raise HTTPException(404, "The community room is not available yet")


router = APIRouter(tags=["community"], dependencies=[Depends(community_available)])

REASONS = {"spam", "harassment", "medical-advice", "private-details", "other"}
RESERVED = {"admin", "moderator", "mod", "doctor", "dr", "caretrail", "support", "system", "team"}
NICK_RE = re.compile(r"^[A-Za-z0-9 _-]+$")
POST_WINDOW = timedelta(minutes=10)
POST_LIMIT = 10
REPORT_WINDOW = timedelta(hours=24)
REPORT_LIMIT = 20


def clean_nickname(raw: str) -> str:
    nick = re.sub(r"\s+", " ", (raw or "").strip())
    if not 2 <= len(nick) <= 24 or not NICK_RE.match(nick):
        raise HTTPException(422, "Nicknames use 2-24 letters, numbers, spaces, - or _")
    if not re.search(r"[A-Za-z]", nick):
        raise HTTPException(422, "Nickname needs at least one letter")
    if sum(ch.isdigit() for ch in nick) >= 7:
        raise HTTPException(422, "Do not use a phone number as your nickname")
    if nick.lower().replace(" ", "") in RESERVED:
        raise HTTPException(422, "That nickname is reserved")
    return nick


def member(db: Session, patient: Patient) -> CommunityMember:
    row = db.get(CommunityMember, patient.id)
    if row is None:
        raise HTTPException(403, "Join the community with a nickname first")
    return row


def not_demo(patient: Patient):
    if patient.is_demo:
        raise HTTPException(403, "The shared demo account cannot use this community action. Create a private account")


# The shared demo account may join and post so app demos can show the room
# (user request, temporary). Its identity is fixed and clearly labeled instead
# of a free nickname; moderation-facing actions (report, block) stay blocked.
DEMO_NICKNAME = "Anjali (demo)"


def clean_body(raw: str) -> str:
    body = re.sub(r"\s+", " ", (raw or "").strip())
    if not 1 <= len(body) <= 500:
        raise HTTPException(422, "Messages are 1-500 characters")
    lowered = body.lower()
    if "http" in lowered or "www." in lowered:
        raise HTTPException(422, "Links are not allowed in the community room")
    return body


def msg_view(row, own_id):
    return {"id": row.id, "author_id": row.patient_id, "nickname": row.nickname, "body": row.body,
            "created_at": row.created_at.isoformat(), "own": row.patient_id == own_id,
            "author_blocked": False}


def blocked_ids(db: Session, patient_id: str) -> set:
    return {b.blocked_patient_id for b in db.query(CommunityBlock).filter_by(blocker_patient_id=patient_id)}


class JoinBody(BaseModel):
    nickname: str = Field(min_length=1, max_length=40)
    consent: bool


@router.post("/community/join")
def join(body: JoinBody, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    if body.consent is not True:
        raise HTTPException(422, "Community guidelines must be accepted to join")
    nick = DEMO_NICKNAME if patient.is_demo else clean_nickname(body.nickname)
    row = db.get(CommunityMember, patient.id)
    if row is None:
        row = CommunityMember(patient_id=patient.id, nickname=nick)
        db.add(row)
    else:
        row.nickname = nick  # rename allowed; past messages keep their snapshot
    audit(db, f"patient:{patient.id}", "community_join", "community", "room", {"nickname": nick})
    db.commit()
    return {"joined": True, "nickname": row.nickname, "suspended": row.suspended}


@router.get("/community/me")
def me(patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    row = db.get(CommunityMember, patient.id)
    if row is None:
        return {"joined": False}
    return {"joined": True, "nickname": row.nickname, "suspended": row.suspended,
            "suspended_reason": row.suspended_reason if row.suspended else ""}


@router.get("/community/messages")
def list_messages(response: Response, before_id: str | None = None, limit: int = 50,
                  patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    member(db, patient)
    response.headers["Cache-Control"] = "no-store"
    limit = max(1, min(limit, 100))
    query = db.query(CommunityMessage).filter(CommunityMessage.removed == False)  # noqa: E712
    if before_id:
        anchor = db.get(CommunityMessage, before_id)
        if anchor is not None:
            query = query.filter(CommunityMessage.created_at < anchor.created_at)
    rows = query.order_by(CommunityMessage.created_at.desc()).limit(limit + 1).all()
    hidden = blocked_ids(db, patient.id)
    page, more = rows[:limit], len(rows) > limit
    items = []
    for row in reversed(page):  # oldest first for chat display
        view = msg_view(row, patient.id)
        if row.patient_id in hidden:
            view["author_blocked"] = True
            view["body"] = ""
            view["nickname"] = "Blocked member"
        items.append(view)
    return {"messages": items, "has_more": more,
            "oldest_id": page[-1].id if page else None}


class PostBody(BaseModel):
    body: str = Field(min_length=1, max_length=1200)


@router.post("/community/messages", status_code=201)
def post_message(body: PostBody, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    mem = member(db, patient)
    if mem.suspended:
        raise HTTPException(403, "Posting is paused for your account after a moderation review")
    text = clean_body(body.body)
    recent = db.query(CommunityMessage).filter(
        CommunityMessage.patient_id == patient.id,
        CommunityMessage.created_at >= utcnow() - POST_WINDOW).count()
    if recent >= POST_LIMIT:
        raise HTTPException(429, "You are posting too quickly. Wait a few minutes and try again")
    row = CommunityMessage(id=uuid.uuid4().hex, patient_id=patient.id, nickname=mem.nickname, body=text)
    db.add(row)
    db.commit()
    return msg_view(row, patient.id)


@router.delete("/community/messages/{message_id}")
def delete_own(message_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    row = db.get(CommunityMessage, message_id)
    if row is None or row.removed:
        raise HTTPException(404, "Message not found")
    if row.patient_id != patient.id:
        raise HTTPException(404, "Message not found")
    row.removed = True
    row.removed_by = "author"
    db.commit()
    return {"removed": True}


class ReportBody(BaseModel):
    reason: str
    detail: str = Field(default="", max_length=300)


@router.post("/community/messages/{message_id}/report", status_code=201)
def report(message_id: str, body: ReportBody, patient: Patient = Depends(get_current_patient),
           db: Session = Depends(get_db)):
    not_demo(patient)
    member(db, patient)
    if body.reason not in REASONS:
        raise HTTPException(422, "Unknown report reason")
    row = db.get(CommunityMessage, message_id)
    if row is None or row.removed:
        raise HTTPException(404, "Message not found")
    if row.patient_id == patient.id:
        raise HTTPException(422, "You cannot report your own message")
    recent = db.query(CommunityReport).filter(
        CommunityReport.reporter_patient_id == patient.id,
        CommunityReport.created_at >= utcnow() - REPORT_WINDOW).count()
    if recent >= REPORT_LIMIT:
        raise HTTPException(429, "Too many reports in a day. Please try again tomorrow")
    existing = db.query(CommunityReport).filter_by(
        message_id=message_id, reporter_patient_id=patient.id, status="open").first()
    if existing is not None:
        return {"reported": True, "id": existing.id, "duplicate": True}
    rep = CommunityReport(id=uuid.uuid4().hex, message_id=message_id,
                          reporter_patient_id=patient.id, reason=body.reason,
                          detail=re.sub(r"\s+", " ", body.detail.strip()))
    db.add(rep)
    audit(db, f"patient:{patient.id}", "community_report", "community_message", message_id, {"reason": body.reason})
    db.commit()
    return {"reported": True, "id": rep.id, "duplicate": False}


class BlockBody(BaseModel):
    patient_id: str


@router.get("/community/blocks")
def list_blocks(patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    member(db, patient)
    rows = db.query(CommunityBlock).filter_by(blocker_patient_id=patient.id).all()
    names = {m.patient_id: m.nickname for m in db.query(CommunityMember).filter(
        CommunityMember.patient_id.in_([r.blocked_patient_id for r in rows] or [""]))}
    return {"blocked": [{"patient_id": r.blocked_patient_id,
                         "nickname": names.get(r.blocked_patient_id, "Former member")} for r in rows]}


@router.post("/community/blocks", status_code=201)
def block(body: BlockBody, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    not_demo(patient)
    member(db, patient)
    if body.patient_id == patient.id:
        raise HTTPException(422, "You cannot block yourself")
    target = db.get(CommunityMember, body.patient_id)
    if target is None:
        raise HTTPException(404, "Community member not found")
    key = f"{patient.id}:{body.patient_id}"
    if db.get(CommunityBlock, key) is None:
        db.add(CommunityBlock(id=key, blocker_patient_id=patient.id, blocked_patient_id=body.patient_id))
        db.commit()
    return {"blocked": True}


@router.delete("/community/blocks/{patient_id}")
def unblock(patient_id: str, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    row = db.get(CommunityBlock, f"{patient.id}:{patient_id}")
    if row is not None:
        db.delete(row)
        db.commit()
    return {"blocked": False}
