"""App-help generative wording: off by default, and only fixed app-guide
answers ever reach the model - medical questions and data-driven answers
(next appointment, reminders, reports) never do.
"""
from app import apphelp_llm
from app.assistant import APP_GUIDE
from tests.conftest import auth, signup


def test_rephrase_disabled_without_flag(monkeypatch):
    monkeypatch.delenv("ASSISTANT_APPHELP_GENERATIVE", raising=False)
    monkeypatch.delenv("GROQ_API_KEY", raising=False)
    assert apphelp_llm.rephrase_app_help(APP_GUIDE["upload"]) is None
    assert apphelp_llm.enabled() is False


def test_rephrase_requires_both_flag_and_key(monkeypatch):
    monkeypatch.setenv("ASSISTANT_APPHELP_GENERATIVE", "1")
    monkeypatch.delenv("GROQ_API_KEY", raising=False)
    assert apphelp_llm.enabled() is False
    monkeypatch.setenv("GROQ_API_KEY", "test-key")
    assert apphelp_llm.enabled() is True


def test_guide_answer_uses_model_only_when_enabled(client, monkeypatch):
    calls = []
    monkeypatch.setattr(apphelp_llm, "rephrase_app_help", lambda g: calls.append(g) or "REWORDED GUIDE")
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    r = client.post("/assistant/chat", json={"message": "How do I change the app language?", "lang": "en"}, headers=auth(token)).json()
    assert r["source"] == "app:language"
    assert r["answer"] == "REWORDED GUIDE"
    assert len(calls) == 1


def test_medical_questions_never_reach_the_model(client, monkeypatch):
    calls = []
    monkeypatch.setattr(apphelp_llm, "rephrase_app_help", lambda g: calls.append(g) or "REWORDED")
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    r = client.post("/assistant/chat", json={"message": "Can I take paracetamol for my back pain?", "lang": "en"}, headers=auth(token)).json()
    assert r["source"] in ("defer-advice", "danger-sign-rule")
    assert "REWORDED" not in r["answer"]
    assert calls == []


def test_data_driven_answers_never_reach_the_model(client, monkeypatch):
    calls = []
    monkeypatch.setattr(apphelp_llm, "rephrase_app_help", lambda g: calls.append(g) or "REWORDED")
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    r = client.post("/assistant/chat", json={"message": "When is my next appointment?", "lang": "en"}, headers=auth(token)).json()
    assert r["source"] == "app:appointments"
    assert "REWORDED" not in r["answer"]
    assert calls == []


def test_mixed_sensitive_question_sends_only_guide_text(client, monkeypatch):
    sent = []
    monkeypatch.setattr(apphelp_llm, "rephrase_app_help", lambda g: sent.append(g) or "REWORDED")
    token = signup(client)["token"]
    client.post("/journeys", json={"lmp": "2026-04-20"}, headers=auth(token))
    question = "How do I change the app language? I take metformin 500mg for diabetes"
    r = client.post("/assistant/chat", json={"message": question, "lang": "en"}, headers=auth(token)).json()
    for payload in sent:
        assert "metformin" not in payload
        assert "diabetes" not in payload
        assert payload in APP_GUIDE.values()
