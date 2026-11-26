"""Team review queue for community reports. Access needs a server-issued moderator
token (moderation_cli), never a patient or clinician session. Every action is audited."""
from datetime import datetime, timedelta, timezone

import hashlib

from fastapi import APIRouter, Depends, Header, HTTPException, Response
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import audit
from ..models import AuditLog, CommunityMember, CommunityMessage, CommunityModerator, CommunityReport

router = APIRouter(tags=["community-moderation"])

# A report stays in the review queue at most REPORT_REVIEW_DAYS without a decision,
# then auto-expires unactioned (the message stays visible - expiry is not a judgment).
# A closed report (actioned, dismissed or expired) is purged REPORT_RETENTION_DAYS
# after it closed. Nothing is kept indefinitely.
REPORT_REVIEW_DAYS = 30
REPORT_RETENTION_DAYS = 30


def purge_expired_reports(db: Session) -> int:
    """Expire unreviewed open reports older than REPORT_REVIEW_DAYS, then permanently
    delete reports closed longer than REPORT_RETENTION_DAYS, plus the community
    moderation audit rows for messages that no longer have any report.
    Returns the number of reports deleted."""
    now = datetime.now(timezone.utc)
    review_cutoff = now - timedelta(days=REPORT_REVIEW_DAYS)
    stale = db.query(CommunityReport).filter(CommunityReport.status == "open",
                                             CommunityReport.created_at < review_cutoff).all()
    for row in stale:
        row.status = "expired"
        row.action = "none"
        row.handled_by = "auto-expire"
        row.handled_at = now
    if stale:
        db.commit()
    cutoff = now - timedelta(days=REPORT_RETENTION_DAYS)
    old = db.query(CommunityReport).filter(CommunityReport.status != "open",
                                           CommunityReport.handled_at.isnot(None),
                                           CommunityReport.handled_at < cutoff).all()
    if not old:
        return 0
    message_ids = {r.message_id for r in old}
    report_ids = [r.id for r in old]
    db.query(CommunityReport).filter(CommunityReport.id.in_(report_ids)).delete(synchronize_session=False)
    still_reported = {row[0] for row in db.query(CommunityReport.message_id)
                      .filter(CommunityReport.message_id.in_(message_ids)).all()}
    orphan_ids = message_ids - still_reported
    if orphan_ids:
        db.query(AuditLog).filter(AuditLog.action.startswith("community_"),
                                  AuditLog.entity == "community_message",
                                  AuditLog.entity_id.in_(orphan_ids)).delete(synchronize_session=False)
    db.commit()
    return len(report_ids)

ACTIONS = {"dismiss", "remove", "remove_and_suspend"}


def get_moderator(authorization: str | None = Header(default=None),
                  db: Session = Depends(get_db)) -> CommunityModerator:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "Missing moderator token")
    digest = hashlib.sha256(authorization.split(None, 1)[1].strip().encode()).hexdigest()
    mod = db.get(CommunityModerator, digest)
    if mod is None:
        raise HTTPException(401, "Invalid moderator token")
    return mod


def report_view(db: Session, rep: CommunityReport):
    msg = db.get(CommunityMessage, rep.message_id)
    reporter = db.get(CommunityMember, rep.reporter_patient_id)
    open_count = db.query(CommunityReport).filter_by(message_id=rep.message_id, status="open").count()
    expires_at = None
    if rep.status == "open":
        expires_at = (rep.created_at + timedelta(days=REPORT_REVIEW_DAYS)).isoformat()
    return {"id": rep.id, "reason": rep.reason, "detail": rep.detail,
            "created_at": rep.created_at.isoformat(), "status": rep.status,
            "open_reports_on_message": open_count, "expires_at": expires_at,
            "reporter_nickname": reporter.nickname if reporter else "Former member",
            "message": None if msg is None else {
                "id": msg.id, "nickname": "(removed account)" if msg.nickname == "" else msg.nickname,
                "body": "[removed]" if msg.removed else msg.body,
                "created_at": msg.created_at.isoformat(), "removed": msg.removed}}


@router.get("/moderation/reports")
def queue(status: str = "open", response: Response = None,
          mod: CommunityModerator = Depends(get_moderator), db: Session = Depends(get_db)):
    response.headers["Cache-Control"] = "no-store"
    if status not in {"open", "actioned", "dismissed", "expired", "all"}:
        raise HTTPException(422, "Unknown status filter")
    purge_expired_reports(db)
    query = db.query(CommunityReport)
    if status != "all":
        query = query.filter(CommunityReport.status == status)
    rows = query.order_by(CommunityReport.created_at.desc()).limit(100).all()
    return {"moderator": mod.name, "reports": [report_view(db, r) for r in rows]}


class ResolveBody(BaseModel):
    action: str
    note: str = Field(default="", max_length=200)


@router.post("/moderation/reports/{report_id}/resolve")
def resolve(report_id: str, body: ResolveBody, mod: CommunityModerator = Depends(get_moderator),
            db: Session = Depends(get_db)):
    if body.action not in ACTIONS:
        raise HTTPException(422, "Unknown moderation action")
    rep = db.get(CommunityReport, report_id)
    if rep is None:
        raise HTTPException(404, "Report not found")
    if rep.status != "open":
        raise HTTPException(409, "Report already handled")
    msg = db.get(CommunityMessage, rep.message_id)
    now = datetime.now(timezone.utc)
    if body.action in {"remove", "remove_and_suspend"} and msg is not None and not msg.removed:
        msg.removed = True
        msg.removed_by = f"moderator:{mod.name}"
    if body.action == "remove_and_suspend" and msg is not None:
        target = db.get(CommunityMember, msg.patient_id)
        if target is not None:
            target.suspended = True
            target.suspended_reason = body.note.strip() or "Community guidelines violation"
    new_status = "dismissed" if body.action == "dismiss" else "actioned"
    # Resolve every open report on the same message with the same outcome.
    siblings = db.query(CommunityReport).filter_by(message_id=rep.message_id, status="open").all()
    for row in siblings:
        row.status = new_status
        row.action = "" if body.action == "dismiss" else body.action
        row.handled_by = mod.name
        row.handled_at = now
    audit(db, f"moderator:{mod.name}", f"community_{body.action}", "community_message",
          rep.message_id, {"note": body.note.strip()[:120]})
    db.commit()
    return {"resolved": True, "reports_closed": len(siblings), "action": body.action}


_PAGE = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>CareTrail community review queue</title><style>
body{font-family:system-ui,sans-serif;max-width:720px;margin:24px auto;padding:0 16px;color:#1a1a2e}
.card{border:1px solid #d8d8e8;border-radius:12px;padding:14px;margin:12px 0}
.msg{background:#f4f4fa;border-radius:8px;padding:10px;margin:8px 0}
button{margin:4px 4px 0 0;padding:8px 12px;border-radius:8px;border:1px solid #999;background:#fff;cursor:pointer}
.remove{background:#b3261e;color:#fff;border-color:#b3261e}.suspend{background:#5a1a75;color:#fff;border-color:#5a1a75}
small{color:#666}input,select{padding:8px;border-radius:8px;border:1px solid #bbb;width:100%;box-sizing:border-box;margin:4px 0}
</style></head><body>
<h2>CareTrail community review queue</h2>
<small>Team tool. Handles flagged chat messages only: spam, harassment, dangerous medical advice, private details. Not a conversation reader, not medical advice.</small>
<div id="login"><input id="tok" type="password" placeholder="Moderator token" autocomplete="off"><button onclick="save()">Open queue</button></div>
<div id="app" style="display:none">
<p><small>Signed in as <b id="who"></b> · <a href="#" onclick="localStorage.removeItem('ctmod');location.reload();return false">sign out</a> · <a href="#" onclick="load();return false">refresh</a></small></p>
<p><small>Retention: an open report auto-expires 30 days after filing if not reviewed (the message stays visible; expiry is not a judgment). Closed reports (actioned, dismissed, expired) are deleted 30 days after closing. Audit rows for a message are kept only while one of its reports survives.</small></p>
<div id="tabs"><button onclick="setStatus('open')">Open</button><button onclick="setStatus('actioned')">Actioned</button><button onclick="setStatus('dismissed')">Dismissed</button><button onclick="setStatus('expired')">Expired</button></div>
<div id="list"></div></div>
<script>
let T=localStorage.getItem('ctmod')||'';
async function api(p,o){const r=await fetch(p,{...o,headers:{'Authorization':'Bearer '+T,'Content-Type':'application/json'}});
 if(r.status===401){localStorage.removeItem('ctmod');location.reload();throw new Error('auth')}return r.json()}
function save(){T=document.getElementById('tok').value.trim();localStorage.setItem('ctmod',T);load()}
async function act(id,a){const note=prompt('Note for the record (optional):')||'';
 await api('/moderation/reports/'+id+'/resolve',{method:'POST',body:JSON.stringify({action:a,note})});load()}
let S='open';
function setStatus(s){S=s;load()}
async function load(){try{const d=await api('/moderation/reports?status='+S);
 document.getElementById('login').style.display='none';document.getElementById('app').style.display='block';
 document.getElementById('who').textContent=d.moderator;
 const L=document.getElementById('list');
 if(!d.reports.length){L.innerHTML='<p>No '+S+' reports.</p>';return}
 L.innerHTML=d.reports.map(r=>`<div class="card"><small>${r.created_at.slice(0,16).replace('T',' ')} · status: <b>${r.status}</b> · reason: <b>${r.reason}</b> · reporter: ${r.reporter_nickname} · ${r.open_reports_on_message} open report(s) on this message${r.status==='open'&&r.expires_at?` · <b>auto-expires ${r.expires_at.slice(0,10)}</b> if not reviewed`:''}</small>
 <div class="msg"><b>${r.message?r.message.nickname:'?'}</b>: ${r.message?r.message.body:'[message gone]'}</div>
 ${r.detail?'<small>Detail: '+r.detail+'</small><br>':''}
 ${r.status==='open'?`<button onclick="act('${r.id}','dismiss')">Dismiss</button>
 <button class="remove" onclick="act('${r.id}','remove')">Remove message</button>
 <button class="suspend" onclick="act('${r.id}','remove_and_suspend')">Remove + suspend author</button>`:''}</div>`).join('')}
 catch(e){}}</script>
<script>if(T)load()</script>
</body></html>"""


@router.get("/moderation/app", response_class=HTMLResponse)
def moderation_page(response: Response):
    # The app-wide CSP (main.py uses setdefault) denies inline script/style and
    # would leave this page dead and unstyled. This route is a self-contained
    # team tool, so it carries its own policy: inline script/style and same-origin
    # fetch only, everything else denied.
    csp = ("default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
           "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
    return HTMLResponse(content=_PAGE, headers={"Cache-Control": "no-store", "Content-Security-Policy": csp})
