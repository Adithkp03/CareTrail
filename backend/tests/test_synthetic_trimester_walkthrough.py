"""Local-only synthetic journeys. No production account or patient records."""
from datetime import date, timedelta
import pytest
from .conftest import auth, signup

@pytest.mark.parametrize("days", [56, 77, 97, 98, 140, 195, 196, 266, 273])
def test_synthetic_lmp_walkthrough(client, monkeypatch, days):
    # No outbound model, translation or TTS requests, even if local env has keys.
    monkeypatch.setattr("app.voice.chat", lambda *args, **kwargs: None)
    monkeypatch.setattr("app.voice.translate", lambda *args, **kwargs: None)
    owner = signup(client, phone="9000000021", name="Synthetic LMP QA")
    other = signup(client, phone="9000000022", name="Synthetic Other Patient")
    headers = auth(owner["token"])
    lmp = (date.today() - timedelta(days=days)).isoformat()
    response = client.post("/journeys", json={"lmp": lmp}, headers=headers)
    assert response.status_code == 201, response.text
    journey = response.json()
    assert journey["lmp"] == lmp
    assert journey["gestational_age"]["weeks"] == days // 7
    assert journey["gestational_age"]["plus_days"] == days % 7
    assert len(journey["milestones"]) == 14
    jid = journey["journey_id"]
    assert client.get(f"/journey/{jid}", headers=headers).status_code == 200
    assert client.get(f"/journey/{jid}/next-up", headers=headers).status_code == 200
    # Visit every care-step detail, including all trimester groups.
    for milestone in journey["milestones"]:
        mid = milestone["id"]
        detail = client.get(f"/milestones/{mid}", headers=headers)
        assert detail.status_code == 200
        assert detail.json()["id"] == mid
        text = client.get(f"/milestones/{mid}/explanation?lang=en", headers=headers)
        assert text.status_code == 200 and text.json()["text"]
        assert client.get(f"/milestones/{mid}", headers=auth(other["token"])).status_code == 404
    nt = next(m for m in journey["milestones"] if m["key"] == "nt_scan")
    if days in (77, 97):
        assert nt["status"] == "now" and nt["overdue"] is False
    if days == 98:
        assert nt["status"] == "now" and nt["overdue"] is True
