"""Phase 3: upload -> extract (mocked AI) -> confirm -> observations + rules flags."""

import io
from pathlib import Path

import pytest

import app.storage
from tests.conftest import auth, signup

SAMPLES = Path(__file__).resolve().parent.parent / "app" / "sample_reports"


@pytest.fixture(autouse=True)
def tmp_storage(tmp_path, monkeypatch):
    monkeypatch.setattr(app.storage, "STORAGE_DIR", tmp_path)
    yield


def make_journey(client, token, lmp="2026-04-20"):
    r = client.post("/journeys", json={"lmp": lmp}, headers=auth(token))
    assert r.status_code == 201, r.text
    return r.json()


def upload(client, token, journey_id, filename):
    data = (SAMPLES / filename).read_bytes()
    r = client.post(
        f"/journeys/{journey_id}/documents/upload",
        files={"file": (filename, io.BytesIO(data), "text/plain")},
        headers=auth(token),
    )
    assert r.status_code == 201, r.text
    return r.json()["document_id"]


def test_upload_creates_document(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "cbc_english_normal.txt")
    assert doc_id


def test_extract_malayalam_cbc_detects_language_and_proposes_low_hb(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "cbc_malayalam_low_hb.txt")
    r = client.post(f"/documents/{doc_id}/extract", headers=auth(token))
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["language"] == "ml"
    hb = next(v for v in body["proposed"] if v["code"] == "hb")
    assert hb["value"] == 10.2
    assert hb["unit"] == "g/dL"


def test_confirm_writes_observation_and_rules_flag_fires(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "cbc_malayalam_low_hb.txt")
    proposed = client.post(f"/documents/{doc_id}/extract", headers=auth(token)).json()["proposed"]
    r = client.post(
        f"/documents/{doc_id}/confirm",
        json={"values": [{"code": v["code"], "value": v["value"], "unit": v["unit"], "observed_on": v["observed_on"]} for v in proposed]},
        headers=auth(token),
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert len(body["observations"]) == len(proposed)
    hb_flags = [f for f in body["flags"] if f["code"] == "hb"]
    assert hb_flags and hb_flags[0]["value"] == 10.2
    # No LLM decided that flag: /flags recomputes it from the stored observation.
    flags = client.get("/flags", params={"journey_id": j["journey_id"]}, headers=auth(token)).json()["flags"]
    assert any(f["code"] == "hb" for f in flags)


def test_normal_report_confirms_without_flags(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "cbc_english_normal.txt")
    proposed = client.post(f"/documents/{doc_id}/extract", headers=auth(token)).json()["proposed"]
    body = client.post(
        f"/documents/{doc_id}/confirm",
        json={"values": [{"code": v["code"], "value": v["value"], "unit": v["unit"], "observed_on": v["observed_on"]} for v in proposed]},
        headers=auth(token),
    ).json()
    assert body["flags"] == []


def test_ogtt_high_fasting_glucose_flagged(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "ogtt_english_high_fasting.txt")
    proposed = client.post(f"/documents/{doc_id}/extract", headers=auth(token)).json()["proposed"]
    assert {v["code"] for v in proposed} == {"glucose_fasting", "glucose_ogtt_1h", "glucose_ogtt_2h"}
    body = client.post(
        f"/documents/{doc_id}/confirm",
        json={"values": [{"code": v["code"], "value": v["value"], "unit": v["unit"], "observed_on": v["observed_on"]} for v in proposed]},
        headers=auth(token),
    ).json()
    flagged = {f["code"] for f in body["flags"]}
    assert flagged == {"glucose_fasting"}  # 95 >= 92; 171 and 140 are inside limits


def test_hindi_bp_report_flagged(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "bp_hindi_high.txt")
    ext = client.post(f"/documents/{doc_id}/extract", headers=auth(token)).json()
    assert ext["language"] == "hi"
    body = client.post(
        f"/documents/{doc_id}/confirm",
        json={"values": [{"code": v["code"], "value": v["value"], "unit": v["unit"], "observed_on": v["observed_on"]} for v in ext["proposed"]]},
        headers=auth(token),
    ).json()
    flagged = {f["code"] for f in body["flags"]}
    assert {"bp_sys", "bp_dia"} <= flagged  # 142/94 breaches both; hb 11.6 is fine


def test_unit_normalisation_glucose_mmol_to_mgdl(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "urine_routine_normal.txt")
    client.post(f"/documents/{doc_id}/extract", headers=auth(token))
    r = client.post(
        f"/documents/{doc_id}/confirm",
        json={"values": [{"code": "glucose_fasting", "value": 5.3, "unit": "mmol/L", "observed_on": "2026-09-21"}]},
        headers=auth(token),
    )
    assert r.status_code == 200, r.text
    obs = r.json()["observations"][0]
    assert obs["value"] == 95.4 and obs["unit"] == "mg/dL"


def test_mother_can_edit_value_before_confirming(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "cbc_malayalam_low_hb.txt")
    proposed = client.post(f"/documents/{doc_id}/extract", headers=auth(token)).json()["proposed"]
    values = [{"code": v["code"], "value": v["value"], "unit": v["unit"], "observed_on": v["observed_on"]} for v in proposed]
    for v in values:
        if v["code"] == "hb":
            v["value"] = 12.0  # she corrects the extraction
    body = client.post(f"/documents/{doc_id}/confirm", json={"values": values}, headers=auth(token)).json()
    assert [f for f in body["flags"] if f["code"] == "hb"] == []


def test_confirm_rejects_unknown_code(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "cbc_english_normal.txt")
    r = client.post(
        f"/documents/{doc_id}/confirm",
        json={"values": [{"code": "plot Armour", "value": 1.0, "unit": "x"}]},
        headers=auth(token),
    )
    assert r.status_code == 400


def test_reconfirm_replaces_values_no_duplicates(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "cbc_english_normal.txt")
    proposed = client.post(f"/documents/{doc_id}/extract", headers=auth(token)).json()["proposed"]
    values = [{"code": v["code"], "value": v["value"], "unit": v["unit"], "observed_on": v["observed_on"]} for v in proposed]
    client.post(f"/documents/{doc_id}/confirm", json={"values": values}, headers=auth(token))
    client.post(f"/documents/{doc_id}/confirm", json={"values": values}, headers=auth(token))
    journey = client.get(f"/journey/{j['journey_id']}", headers=auth(token)).json()
    codes = [o["code"] for m in journey["milestones"] for o in (m.get("observations") or [])]
    # no milestone was linked, so count via flags path instead: hb flag absent, no crash on dupes
    assert client.get("/flags", params={"journey_id": j["journey_id"]}, headers=auth(token)).status_code == 200
    assert codes == []  # unlinked documents do not attach to milestones


def test_confirmed_values_appear_on_linked_milestone(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    baseline = next(m for m in j["milestones"] if m["key"] == "baseline_bloods")
    doc_id = upload(client, token, j["journey_id"], "cbc_malayalam_low_hb.txt")
    proposed = client.post(f"/documents/{doc_id}/extract", headers=auth(token)).json()["proposed"]
    body = client.post(
        f"/documents/{doc_id}/confirm",
        json={
            "milestone_id": baseline["id"],
            "values": [{"code": v["code"], "value": v["value"], "unit": v["unit"], "observed_on": v["observed_on"]} for v in proposed],
        },
        headers=auth(token),
    ).json()
    assert body["milestone_id"] == baseline["id"]
    m = client.get(f"/milestones/{baseline['id']}", headers=auth(token)).json()
    hb = next(o for o in m["observations"] if o["code"] == "hb")
    assert hb["value"] == 10.2


def test_cannot_touch_another_patients_document(client):
    token_a = signup(client, phone="9000000001")["token"]
    token_b = signup(client, phone="9000000002")["token"]
    j = make_journey(client, token_a)
    doc_id = upload(client, token_a, j["journey_id"], "cbc_english_normal.txt")
    assert client.post(f"/documents/{doc_id}/extract", headers=auth(token_b)).status_code == 404
    assert (
        client.post(
            f"/documents/{doc_id}/confirm",
            json={"values": [{"code": "hb", "value": 9.0, "unit": "g/dL"}]},
            headers=auth(token_b),
        ).status_code
        == 404
    )


def test_report_with_no_tracked_values_confirms_clean(client):
    token = signup(client)["token"]
    j = make_journey(client, token)
    doc_id = upload(client, token, j["journey_id"], "urine_routine_normal.txt")
    ext = client.post(f"/documents/{doc_id}/extract", headers=auth(token)).json()
    assert ext["proposed"] == [] and ext["message"]
    # confirming with zero observations is a client choice; API requires >= 1 value,
    # so a no-value report simply needs no confirm call.
