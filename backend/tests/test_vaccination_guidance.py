"""Discussion-only guidance cannot create, backfill or relabel dose obligations."""
from copy import deepcopy
from datetime import date, timedelta

import pytest
from sqlalchemy import event

from app.database import get_db
from app.models import Milestone
from app.main import app
from app.template_loader import load_template
from .conftest import signup, auth


@pytest.mark.parametrize("days", [56, 98, 140, 196, 266])
def test_new_antenatal_guidance_is_metadata_not_a_dose(client, days):
    headers = auth(signup(client)["token"])
    r = client.post("/journeys", json={"lmp": (date.today() - timedelta(days=days)).isoformat()}, headers=headers)
    assert r.status_code == 201, r.text
    j = r.json()
    guide = j["vaccination_guidance"]
    assert guide["kind"] == "clinician_discussion"
    assert "16 and 20" in guide["timing"]
    assert "two Td/TT doses" in guide["booster"] and "last 3 years" in guide["booster"]
    assert "second Td dose" in guide["tdap"] and "not mean automatic extra doses" in guide["tdap"]
    assert not ({"status", "overdue", "completed_at", "scheduled_date", "eligible"} & guide.keys())
    assert len(j["milestones"]) == 14
    assert {m["status"] for m in j["milestones"]} <= {"now", "upcoming", "next", "done"}
    assert not any(m["key"] in {"td_dose_1", "td_dose_2", "td_booster"} for m in j["milestones"])
    assert sum(j["summary"].values()) == 14


def test_existing_records_survive_guidance_read_without_database_writes(client, monkeypatch):
    # Create with the prior template shape, then make current optional metadata available.
    import app.routers.journeys as routes
    current = deepcopy(load_template())
    previous = deepcopy(current)
    previous.pop("vaccination_guidance")
    monkeypatch.setattr(routes, "load_template", lambda *args, **kwargs: previous)
    headers = auth(signup(client)["token"])
    created = client.post("/journeys", json={"lmp": (date.today() - timedelta(days=210)).isoformat()}, headers=headers).json()
    jid = created["journey_id"]
    vaccine = next(m for m in created["milestones"] if m["key"] == "tdap_vaccine")
    visit = next(m for m in created["milestones"] if m["key"] == "review_38w")
    assert client.post(f'/milestones/{vaccine["id"]}/complete', json={"completed_at": date.today().isoformat()}, headers=headers).status_code == 200
    assert client.post(f'/milestones/{visit["id"]}/schedule', json={"scheduled_date": (date.today()+timedelta(days=10)).isoformat()}, headers=headers).status_code == 200
    before = client.get(f"/journey/{jid}", headers=headers).json()
    assert before["vaccination_guidance"] is None
    db_generator = app.dependency_overrides[get_db]()
    db = next(db_generator)
    engine = db.get_bind()
    def snapshot():
        return [(m.id, m.key, m.title, m.type, m.prep_notes, m.completed_at, m.scheduled_date)
                for m in db.query(Milestone).filter(Milestone.journey_id == jid).order_by(Milestone.id)]
    stored_before = snapshot()
    writes = []
    def capture_writes(conn, cursor, statement, parameters, context, executemany):
        if statement.lstrip().split(None, 1)[0].upper() in {"INSERT", "UPDATE", "DELETE", "REPLACE"}:
            writes.append(statement)
    monkeypatch.setattr(routes, "load_template", lambda *args, **kwargs: current)
    event.listen(engine, "before_cursor_execute", capture_writes)
    try:
        after = client.get(f"/journey/{jid}", headers=headers).json()
        repeated = client.get(f"/journey/{jid}", headers=headers).json()
        assert after["vaccination_guidance"] == current["vaccination_guidance"]
        assert after["milestones"] == before["milestones"] == repeated["milestones"]
        assert after["summary"] == before["summary"]
        assert after["next_appointment"] == before["next_appointment"]
        assert snapshot() == stored_before
        assert writes == []
    finally:
        event.remove(engine, "before_cursor_execute", capture_writes)
        try:
            next(db_generator)
        except StopIteration:
            pass


def test_optional_guidance_does_not_change_exam_notes_or_hospital_warning():
    template = load_template()
    exam = next(m for m in template["milestones"] if m["key"] == "review_38w")
    assert "induction" not in exam["prep_notes"].lower()
    assert "consent" not in exam["prep_notes"].lower()
    assert "regular pains, leaking, bleeding" in exam["prep_notes"]
