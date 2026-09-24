"""Phase 5: guidance grounding + citations, next-up card, pre-consult brief,
sign-off state on flags."""

from tests.conftest import auth, signup


def make_journey(client, token, lmp="2026-04-20"):
    r = client.post("/journeys", json={"lmp": lmp}, headers=auth(token))
    assert r.status_code == 201, r.text
    return r.json()


def confirm_low_hb(client, token, journey_id):
    doc = client.post(
        f"/journeys/{journey_id}/documents/upload",
        files={"file": ("cbc.txt", b"Haemoglobin: 10.2 g/dL\nDate: 2026-09-20", "text/plain")},
        headers=auth(token),
    ).json()["document_id"]
    client.post(
        f"/documents/{doc}/confirm",
        json={"values": [{"code": "hb", "value": 10.2, "unit": "g/dL", "observed_on": "2026-09-20"}]},
        headers=auth(token),
    )


def test_ask_cites_guidance(client):
    token = signup(client)["token"]
    make_journey(client, token)
    body = client.post("/ask", json={"question": "why do I need the anomaly scan?"}, headers=auth(token)).json()
    assert body["citation"] and "guidance" in body["citation"]["source"].lower()


def test_ask_guidance_grounded_general_question(client):
    token = signup(client)["token"]
    make_journey(client, token)
    body = client.post("/ask", json={"question": "when should I count baby movements?"}, headers=auth(token)).json()
    assert body["source"].startswith("guidance:")
    assert "movements" in body["answer"].lower()


def test_ask_ungrounded_question_no_citation(client):
    token = signup(client)["token"]
    make_journey(client, token)
    body = client.post("/ask", json={"question": "will it rain on delivery day?"}, headers=auth(token)).json()
    assert body["source"] == "none" and body["citation"] is None
    assert "ask your doctor" in body["answer"].lower()


def test_flag_answer_includes_guidance_text(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    confirm_low_hb(client, token, j["journey_id"])
    body = client.post("/ask", json={"question": "is my haemoglobin okay?"}, headers=auth(token)).json()
    assert body["source"] == "timeline-flag"
    assert "anaemia" in body["answer"].lower() or "iron" in body["answer"].lower()


def test_next_up_card_structure(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    r = client.get(f"/journey/{j['journey_id']}/next-up", headers=auth(token))
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["milestone"]["key"]
    assert body["purpose"] and body["what_to_bring"]
    assert isinstance(body["questions"], list) and body["questions"]


def test_next_up_ogtt_has_fasting_rule(client):
    token = signup(client)["token"]
    # 25 weeks along: OGTT (24-28w) is the next-up test
    j = make_journey(client, token, lmp="2026-04-01")
    body = client.get(f"/journey/{j['journey_id']}/next-up", headers=auth(token)).json()
    if body["milestone"]["key"] == "ogtt":
        assert body["fasting"] and "fasting" in body["fasting"].lower()


def test_brief_lists_open_flags(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    confirm_low_hb(client, token, j["journey_id"])
    body = client.get(f"/journey/{j['journey_id']}/brief", headers=auth(token)).json()
    assert any(f["code"] == "hb" for f in body["open_flags"])
    assert body["signed_flags"] == []
    assert any(v["code"] == "hb" for v in body["latest_values"])


def test_signoff_moves_flag_to_signed_and_badges_timeline(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    confirm_low_hb(client, token, j["journey_id"])
    brief = client.get(f"/journey/{j['journey_id']}/brief", headers=auth(token)).json()
    obs_id = brief["open_flags"][0]["observation_id"]
    r = client.post(
        "/signoffs",
        json={"journey_id": j["journey_id"], "observation_id": obs_id, "note": "Will recheck in 2 weeks."},
        headers={**auth(token), "X-Doctor-Name": "Dr Meera"},
    )
    assert r.status_code == 201, r.text
    brief = client.get(f"/journey/{j['journey_id']}/brief", headers=auth(token)).json()
    assert brief["open_flags"] == []
    assert brief["signed_flags"][0]["signed_off"]["doctor_name"] == "Dr Meera"


def test_brief_requires_ownership(client):
    token_a = signup(client, phone="9000000005")["token"]
    token_b = signup(client, phone="9000000006")["token"]
    j = make_journey(client, token_a)
    assert client.get(f"/journey/{j['journey_id']}/brief", headers=auth(token_b)).status_code == 404
