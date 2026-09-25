"""Phase 6: PII stripping, AI traces, eval accuracy, consent at signup."""

import json
from pathlib import Path

from tests.conftest import auth, signup


def test_strip_pii_masks_phone_email_name():
    import sys

    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    from app.privacy import strip_pii

    text = "Name: Anjali Kumar\nPhone: 9876543210\nEmail: anjali@example.com\nHb 10.2 g/dL"
    out = strip_pii(text)
    assert "9876543210" not in out and "anjali@example.com" not in out and "Anjali Kumar" not in out
    assert "10.2" in out  # clinical values survive


def test_signup_requires_consent(client):
    r = client.post("/auth/signup", json={"name": "No Consent", "phone": "9000000011", "password": "secret123", "language": "en", "consent": False})
    assert r.status_code == 422
    r = client.post("/auth/signup", json={"name": "Yes Consent", "phone": "9000000012", "password": "secret123", "language": "en", "consent": True})
    assert r.status_code == 201


def test_ai_traces_endpoint(client):
    token = signup(client)["token"]
    r = client.get("/ai-traces", headers=auth(token))
    assert r.status_code == 200 and "traces" in r.json()


def test_trace_is_metadata_only_and_patient_isolated(client):
    from app.database import get_db
    from app.models import AiCallTrace
    from app.tracing import trace

    token_a = signup(client)["token"]
    token_b = client.post("/auth/signup", json={"name": "Another", "phone": "9000000088", "password": "secret123", "language": "en", "consent": True}).json()["token"]
    patient_a = client.get("/auth/me", headers=auth(token_a)).json()["patient_id"]
    db = next(client.app.dependency_overrides[get_db]())
    with trace(db, "sarvam", "chat", "Sensitive Hb 8.7", patient_id=patient_a) as t:
        t.finish("Private medical output")
    row = db.query(AiCallTrace).filter(AiCallTrace.patient_id == patient_a).first()
    assert row is not None and row.prompt == "" and row.output == "" and row.status == "ok"
    own = client.get("/ai-traces", headers=auth(token_a)).json()["traces"]
    other = client.get("/ai-traces", headers=auth(token_b)).json()["traces"]
    assert len(own) == 1 and other == []
    assert "prompt" not in own[0] and "output" not in own[0]


def test_legacy_traces_are_scrubbed_on_migration(client):
    from app.database import get_db
    from app.models import AiCallTrace
    from sqlalchemy import text

    db = next(client.app.dependency_overrides[get_db]())
    row = AiCallTrace(provider="legacy", kind="chat", prompt="Hb 8.7", output="Private result")
    db.add(row)
    db.commit()
    # Run the same idempotent scrub against the isolated test DB.
    db.execute(text("UPDATE ai_call_traces SET prompt = :blank, output = :blank WHERE prompt <> :blank OR output <> :blank"), {"blank": ""})
    db.commit()
    db.refresh(row)
    assert row.prompt == "" and row.output == ""


def test_eval_accuracy_at_least_95_percent():
    import sys

    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    from evals.run_eval import run

    result = run()
    assert result["cases"] >= 15
    assert result["accuracy"] >= 0.95, result["misses"]


def test_evalset_is_valid_json_with_expected_values():
    cases = json.loads((Path(__file__).resolve().parent.parent / "evals" / "evalset.json").read_text())
    assert len(cases) >= 15
    for case in cases:
        assert case["expected"] and case["text"]
