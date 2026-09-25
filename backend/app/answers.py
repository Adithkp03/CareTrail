"""The Ask pipeline's answering half (Phase 4; full RAG grounding is Phase 5).

Safety first: the doctor's danger-sign list is matched IN CODE against every
question. A match always returns "contact your doctor now" - no model is involved.
Other questions are answered from the mother's own timeline data and the curated
explanations; when Sarvam is available it words the answer from that same grounded
context. No grounding found means "ask your doctor", never a guess.
"""

import re

from sqlalchemy.orm import Session

from . import voice
from .explanations import MILESTONE_EXPLANATIONS_EN, MILESTONE_EXPLANATIONS_LOCAL
from .flags import compute_flags
from .guidance import retrieve
from .models import Journey

DANGER_SIGN_KEYWORDS = {
    "en": ["bleeding", "leaking", "headache", "blurred vision", "reduced movements", "less movement", "no movement", "not moving", "stopped moving", "abdominal pain", "fever", "swelling"],
    "ml": ["രക്തസ്രാവം", "ചോർച്ച", "തലവേദന", "കുഞ്ഞിന്റെ ചലനം", "വയറ്റുവേദന", "പനി", "നീര്"],
    "hi": ["खून", "रक्तस्राव", "सिरदर्द", "बच्चे की हलचल", "पेट दर्द", "बुखार", "सूजन"],
}

CONTACT_DOCTOR = {
    "en": "This can be a warning sign. Please contact your doctor or go to the hospital now.",
    "ml": "ഇത് ഒരു മുന്നറിയിപ്പ് ലക്ഷണമായേക്കാം. ദയവായി ഉടൻ ഡോക്ടറെ ബന്ധപ്പെടുക അല്ലെങ്കിൽ ആശുപത്രിയിൽ പോകുക.",
    "hi": "यह एक चेतावनी संकेत हो सकता है। कृपया अभी अपने डॉक्टर से संपर्क करें या अस्पताल जाएं।",
}

ASK_DOCTOR = {
    "en": "I don't have enough information to answer that safely. Please ask your doctor.",
    "ml": "ഇതിന് സുരക്ഷിതമായി ഉത്തരം നൽകാൻ എന്റെ പക്കൽ വിവരമില്ല. ദയവായി ഡോക്ടറോട് ചോദിക്കുക.",
    "hi": "इसका सुरक्षित जवाब देने के लिए मेरे पास पर्याप्त जानकारी नहीं है। कृपया अपने डॉक्टर से पूछें।",
}


def detect_question_language(text: str) -> str:
    malayalam = sum(1 for c in text if "\u0d00" <= c <= "\u0d7f")
    devanagari = sum(1 for c in text if "\u0900" <= c <= "\u097f")
    if malayalam > 2:
        return "ml"
    if devanagari > 2:
        return "hi"
    return "en"


DANGER_SIGN_PAIRS = [("reduced", "movement"), ("less", "movement"), ("no", "movement"), ("not", "moving"), ("stopped", "moving")]


def _matches_danger_sign(question: str) -> bool:
    q = question.lower()
    for words in DANGER_SIGN_KEYWORDS.values():
        for w in words:
            if w.lower() in q:
                return True
    return any(a in q and b in q for a, b in DANGER_SIGN_PAIRS)


def _milestone_from_question(db: Session, journey: Journey, question: str) -> dict | None:
    q = question.lower()
    best: tuple[int, object] = (0, None)
    for m in journey.milestones:
        words = [w for w in re.split(r"[^a-z]+", m.title.lower()) if len(w) > 3]
        score = sum(1 for w in words if w in q)
        if score > best[0]:
            best = (score, m)
    m = best[1]
    if m is None:
        return None
    text = MILESTONE_EXPLANATIONS_EN.get(m.key) or m.prep_notes or m.title
    return {"key": m.key, "title": m.title, "text_en": text}


def answer_question(db: Session, journey: Journey, question: str, language: str, template: dict) -> dict:
    """Grounded answer with code-matched safety rails. Returns text in `language`."""
    if _matches_danger_sign(question):
        return {"answer": CONTACT_DOCTOR.get(language, CONTACT_DOCTOR["en"]), "source": "danger-sign-rule", "urgent": True}

    # Match and retrieve on an English rendering too, so Malayalam/Hindi questions
    # find the same milestones and guidance as English ones.
    search_q = question
    if language != "en":
        try:
            q_en = voice.translate(question, "en", source_language=language)
        except Exception:
            q_en = None
        if q_en:
            search_q = f"{question} {q_en}"
    # A translated question must pass through the same deterministic warning gate.
    # Without translation, unfamiliar-language questions get the safe fallback.
    if search_q != question and _matches_danger_sign(search_q):
        return {"answer": CONTACT_DOCTOR.get(language, CONTACT_DOCTOR["en"]), "source": "danger-sign-rule", "urgent": True}
    if language not in ("en", "ml", "hi") and search_q == question:
        return {"answer": ASK_DOCTOR["en"], "source": "none", "urgent": False, "citation": None}
    hit = _milestone_from_question(db, journey, search_q)
    flags = compute_flags(db, journey.id, template)
    flag_mentioned = next((f for f in flags if f["label"].lower() in search_q.lower() or f["code"] in search_q.lower()), None)
    guidance = retrieve(q_en if language != "en" and search_q != question else question) or (retrieve(question) if search_q != question else [])
    citation = {"source": guidance[0]["source"], "topic": guidance[0]["topic"]} if guidance else None

    if flag_mentioned:
        base_en = (
            f"Your latest {flag_mentioned['label']} was {flag_mentioned['value']} {flag_mentioned['unit']}. "
            f"{flag_mentioned['message']} Your doctor has this on their review list."
        )
        if guidance:
            base_en += " " + guidance[0]["text"]
        source = "timeline-flag"
    elif hit:
        base_en = hit["text_en"]
        source = f"milestone:{hit['key']}"
    elif guidance:
        base_en = guidance[0]["text"]
        source = f"guidance:{guidance[0]['id']}"
    else:
        return {"answer": ASK_DOCTOR.get(language, ASK_DOCTOR["en"]), "source": "none", "urgent": False, "citation": None}

    if language == "en":
        return {"answer": base_en, "source": source, "urgent": False, "citation": citation}
    if hit and (hit["key"], language) in MILESTONE_EXPLANATIONS_LOCAL and not flag_mentioned:
        return {"answer": MILESTONE_EXPLANATIONS_LOCAL[(hit["key"], language)], "source": source, "urgent": False, "citation": citation}
    translated = voice.translate(base_en, language)
    if translated:
        return {"answer": translated, "source": source, "urgent": False, "citation": citation}
    return {"answer": base_en, "source": source, "urgent": False, "citation": citation, "note": "shown in English - translation needs the Sarvam key"}
