"""The general-help assistant (Sprint 6). NOT a medical adviser.

Hard boundary, enforced in code before any model is involved:
- Danger-sign words always return "contact your doctor now" (reuses the Ask
  pipeline's deterministic gate, including the translated-question pass).
- Questions seeking personal medical guidance (symptoms, medication, doses,
  "should I...") get a fixed deferral - never an individualized answer.
- App-help questions are answered deterministically from the mother's own data.
- General maternal-information questions are answered from the curated
  FOGSI/WHO-aligned guidance corpus only; Sarvam, when configured, may reword
  that approved text but is instructed to add nothing.
- Anything else defers. Every deferral offers the ask-the-doctor route.
"""

from datetime import date

from sqlalchemy.orm import Session

from . import voice
from .answers import CONTACT_DOCTOR, _matches_danger_sign, detect_question_language
from .explanations import MILESTONE_EXPLANATIONS_EN
from .guidance import retrieve
from .models import Document, Journey, Milestone
from .reminders import reminder_items

DEFER = {
    "en": "I can't help with personal medical questions - I'm here for general information and app help only. Please ask your doctor about this. You can send them a question directly from CareTrail.",
    "ml": "വ്യക്തിഗത വൈദ്യ ചോദ്യങ്ങളിൽ എനിക്ക് സഹായിക്കാനാവില്ല - ഞങ്ങൾ പൊതുവായ വിവരങ്ങളും ആപ്പ് സഹായവും മാത്രമേ നൽകൂ. ദയവായി ഇത് ഡോക്ടറോട് ചോദിക്കുക. CareTrail-ൽ നിന്ന് നേരിട്ട് ചോദ്യം അയയ്ക്കാവുന്നതാണ്.",
    "hi": "मैं निजी चिकित्सा सवालों में मदद नहीं कर सकता - मैं सिर्फ सामान्य जानकारी और ऐप सहायता के लिए हूं। कृपया यह अपने डॉक्टर से पूछें। आप CareTrail से सीधे उन्हें सवाल भेज सकते हैं।",
}

NO_GROUND = {
    "en": "I don't have reliable information on that. I can help with your appointments, reminders, reports and general pregnancy information. For anything else, please ask your doctor.",
    "ml": "ഇതിനെക്കുറിച്ച് എന്റെ പക്കൽ വിശ്വാസയോഗ്യമായ വിവരമില്ല. അപ്പോയിന്റ്മെന്റുകൾ, റിമൈൻഡറുകൾ, റിപ്പോർട്ടുകൾ, ഗർഭധാരണത്തെക്കുറിച്ചുള്ള പൊതുവിവരങ്ങൾ എന്നിവയിൽ എനിക്ക് സഹായിക്കാനാകും. മറ്റുള്ളവയ്ക്ക് ഡോക്ടറോട് ചോദിക്കുക.",
    "hi": "मेरे पास इस बारे में विश्वसनीय जानकारी नहीं है। मैं आपके अपॉइंटमेंट, रिमाइंडर, रिपोर्ट और सामान्य गर्भावस्था जानकारी में मदद कर सकता हूं। अन्य किसी भी बात के लिए अपने डॉक्टर से पूछें।",
}

# Personal-medical-guidance markers. Anything matching defers; the assistant
# never assesses a symptom or recommends a medicine, dose or supplement.
ADVICE_MARKERS = [
    "should i", "can i take", "is it safe to take", "shall i take", "may i take",
    "tablet", "medicine", "medication", "dose", "dosage", "mg ", "painkiller",
    "antibiotic", "supplement", "iron tablet", "folic", "vaccination safe",
    "i have pain", "i am having pain", "i feel ", "i am feeling", "i've been feeling",
    "is it normal that i", "is this normal", "is my ", "my baby is", "my baby has",
    "what should i do", "what can i do about", "how do i treat", "how to treat",
    "cramps", "discharge", "dizzy", "dizziness", "vomiting", "vomit", "nausea",
    "backache", "back pain", "stomach pain", "tummy pain", "itching", "rash",
    "constipation", "diarrhea", "diarrhoea", "sleepless", "can't sleep", "cant sleep",
    "മരുന്ന്", "ഗുളിക", "വേദന", "ഛർദ്ദി", "ബഹിസ്കാരം",
    "दवा", "गोली", "दर्द", "उल्टी", "चक्कर", "मुझे", "मेरे बच्चे",
]

APP_GUIDE = {
    "upload": "To add a report: open a check-up from your timeline and tap Upload, or use the Reports section. PDFs and photos are accepted, and CareTrail reads common values like haemoglobin, blood pressure and glucose for you to confirm.",
    "movements": "The Movements tab helps you count baby movements. From week 28, count daily at roughly the same time; the app keeps your counts on the timeline.",
    "export": "You can download a copy of everything CareTrail holds about you from Account > Data safety > Export. You can also delete your account and data there.",
    "language": "You can switch the app's language from the language picker on the home screen. CareTrail supports English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi and Assamese.",
    "doctor_link": "To let your doctor see your timeline and answer your questions: open the Doctor section and enter your doctor's CareTrail email. They must have a verified clinician account first. You can remove their access any time.",
    "referral": "The Referral section prepares a summary you can carry to another facility - you choose exactly what to include before exporting it.",
}

APP_INTENT_KEYWORDS = {
    "appointment": ["appointment", "next visit", "next check", "when is my", "scheduled", "coming up", "upcoming", "doctor visit", "scan date", "അപ്പോയിന്റ്മെന്റ്", "अपॉइंटमेंट", "मुलाकात"],
    "reminder": ["reminder", "remind", "notification", "alert", "റിമൈൻഡർ", "रिमाइंडर", "याद"],
    "report": ["report", "upload", "document", "scan report", "lab result", "where are my reports", "റിപ്പോർട്ട്", "रिपोर्ट"],
    "upload": ["how do i upload", "how to upload", "add a report", "add report"],
    "movements": ["count movements", "movement count", "kick count", "how to count"],
    "export": ["export", "download my data", "delete my data", "my data"],
    "language": ["change language", "switch language", "language"],
    "doctor_link": ["link my doctor", "add my doctor", "connect doctor", "grant my doctor", "remove my doctor"],
    "referral": ["referral", "another hospital", "another facility", "transfer"],
}


def _fixed(strings: dict, language: str) -> str:
    """Fixed safety copy in en/ml/hi; translate on the fly for other Sarvam languages."""
    if language in strings:
        return strings[language]
    translated = voice.translate(strings["en"], language) if language in voice.LANG_NAMES else None
    return translated or strings["en"]


def _matches_any(text: str, keywords: list[str]) -> bool:
    t = text.lower()
    return any(k in t for k in keywords)


def _app_help(db: Session, journey: Journey | None, q: str, language: str) -> dict | None:
    if _matches_any(q, APP_INTENT_KEYWORDS["appointment"]):
        if journey is None:
            return {"answer": "Once your pregnancy journey is set up, your appointments and their dates appear here.", "source": "app:appointments"}
        upcoming = [m for m in journey.milestones if m.scheduled_date and m.scheduled_date >= date.today() and not m.completed_at]
        upcoming.sort(key=lambda m: m.scheduled_date)
        if upcoming:
            m = upcoming[0]
            more = f" {len(upcoming) - 1} more booked after that." if len(upcoming) > 1 else ""
            return {"answer": f"Your next booked appointment is {m.title} on {m.scheduled_date.isoformat()}.{more} The full list is in your timeline and reminders.", "source": "app:appointments"}
        nxt = next((m for m in journey.milestones if not m.completed_at), None)
        if nxt:
            return {"answer": f"You have no booked appointment date right now. Your next suggested check-up is {nxt.title} (weeks {nxt.window_start_weeks:g}-{nxt.window_end_weeks:g}). Book it with your clinic, then add the date from its page so reminders can warn you.", "source": "app:appointments"}
        return {"answer": "Your care plan has no upcoming check-ups. If you think something is missing, your clinic can confirm the schedule.", "source": "app:appointments"}
    if _matches_any(q, APP_INTENT_KEYWORDS["reminder"]):
        if journey is None:
            return {"answer": "Once your journey is set up, reminders about upcoming and missed care appear in the Reminders tab.", "source": "app:reminders"}
        items = [i for i in reminder_items(db, journey) if not i["dismissed"]]
        unread = [i for i in items if not i["read"]]
        if unread:
            return {"answer": f"You have {len(unread)} unread reminder{'s' if len(unread) != 1 else ''}. Latest: {unread[0]['title']} - {unread[0]['message']}", "source": "app:reminders"}
        return {"answer": "You have no pending reminders right now. Reminders appear in the Reminders tab when an appointment is within 3 days, a booked date passes, or a care window is missed.", "source": "app:reminders"}
    if _matches_any(q, APP_INTENT_KEYWORDS["report"]):
        if journey is not None:
            docs = db.query(Document).filter(Document.journey_id == journey.id).order_by(Document.uploaded_at.desc()).all()
            if docs:
                names = ", ".join(d.filename for d in docs[:3])
                return {"answer": f"You have {len(docs)} report{'s' if len(docs) != 1 else ''} saved. Most recent: {names}. Open a check-up from your timeline to see its reports, or use Upload to add one.", "source": "app:reports"}
        return {"answer": APP_GUIDE["upload"], "source": "app:reports"}
    for key in ("upload", "movements", "export", "language", "doctor_link", "referral"):
        if _matches_any(q, APP_INTENT_KEYWORDS[key]):
            return {"answer": APP_GUIDE[key], "source": f"app:{key}"}
    return None


def answer_chat(db: Session, journey: Journey | None, question: str, language: str) -> dict:
    """Scoped answer: {answer, source, urgent, suggest_doctor, language}."""
    # 1. Danger signs first, on the question and its English rendering.
    if _matches_danger_sign(question):
        return {"answer": _fixed(CONTACT_DOCTOR, language), "source": "danger-sign-rule", "urgent": True, "suggest_doctor": True, "language": language}
    search_q, q_en = question, None
    if language != "en":
        try:
            q_en = voice.translate(question, "en", source_language=language)
        except Exception:
            q_en = None
        if q_en:
            search_q = f"{question} {q_en}"
    if search_q != question and _matches_danger_sign(search_q):
        return {"answer": _fixed(CONTACT_DOCTOR, language), "source": "danger-sign-rule", "urgent": True, "suggest_doctor": True, "language": language}
    # Without translation, unfamiliar-language questions get the safe fallback.
    if language not in ("en", "ml", "hi") and search_q == question:
        return {"answer": _fixed(NO_GROUND, language), "source": "no-translation", "urgent": False, "suggest_doctor": True, "language": language}

    match_q = q_en or question

    # 2. App help is deterministic and safe even for advice-flavored phrasing.
    help_hit = _app_help(db, journey, match_q, language)
    if help_hit:
        # App-help answers carry her own data (dates, titles) and are templated;
        # translate for display only - this text never reaches the chat model.
        if language != "en":
            translated = voice.translate(help_hit["answer"], language)
            if translated:
                help_hit["answer"] = translated
        return {**help_hit, "urgent": False, "suggest_doctor": False, "language": language}

    # 3. Personal medical guidance: fixed deferral, never an answer.
    if _matches_any(match_q, ADVICE_MARKERS) or _matches_any(question, ADVICE_MARKERS):
        return {"answer": _fixed(DEFER, language), "source": "defer-advice", "urgent": False, "suggest_doctor": True, "language": language}

    # 4. General information from the curated corpus only.
    guidance = retrieve(match_q) or (retrieve(question) if match_q != question else [])
    if not guidance and journey is not None:
        # Milestone explanations count as curated general information too.
        q_low = match_q.lower()
        for m in journey.milestones:
            if m.key.replace("_", " ") in q_low or m.title.lower() in q_low:
                text_en = MILESTONE_EXPLANATIONS_EN.get(m.key)
                if text_en:
                    guidance = [{"id": f"milestone:{m.key}", "topic": m.title, "source": "CareTrail care guide", "text": text_en}]
                break
    if not guidance:
        return {"answer": _fixed(NO_GROUND, language), "source": "none", "urgent": False, "suggest_doctor": True, "language": language}

    base_en = guidance[0]["text"]
    source = f"guidance:{guidance[0]['id']}"
    if language != "en":
        translated = voice.translate(base_en, language)
        if translated:
            return {"answer": translated, "source": source, "urgent": False, "suggest_doctor": False, "language": language}
    if voice.available():
        worded = voice.chat(
            "You are CareTrail's general help assistant. Rewrite the APPROVED INFORMATION below in simple, warm words"
            f" in {'English' if language == 'en' else language}. Rules: add no medical advice, diagnosis, treatment,"
            " medicine or dosage suggestions; add no new facts; keep it under 80 words; end with: For personal concerns,"
            " please ask your doctor.\n\nAPPROVED INFORMATION:\n" + base_en,
            db=db,
        )
        if worded:
            return {"answer": worded, "source": source, "urgent": False, "suggest_doctor": False, "language": language}
    out = base_en if language == "en" else base_en
    note = None if language == "en" else "shown in English - translation needs the Sarvam key"
    return {"answer": out, "source": source, "urgent": False, "suggest_doctor": False, "language": language, **({"note": note} if note else {})}
