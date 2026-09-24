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


def test_trace_written_on_provider_call(client):
    from app.database import get_db
    from app.models import AiCallTrace
    from app.tracing import trace

    db = next(client.app.dependency_overrides[get_db]())
    with trace(db, "sarvam", "chat", "test prompt") as t:
        t.finish("test output")
    rows = db.query(AiCallTrace).filter(AiCallTrace.prompt == "test prompt").all()
    assert len(rows) == 1 and rows[0].output == "test output" and rows[0].status == "ok"


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
