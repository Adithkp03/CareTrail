"""Plain-language explanations (Phase 4): per milestone and per result.

Fixed shape: what it is, why it matters, what normal looks like, what to do.
Generated once per (key, language, template version) and cached in the DB - a page
load never re-generates. With SARVAM_API_KEY the text comes from Sarvam and is
translated with Sarvam Translate; without keys we serve curated content (English
for everything, plus Malayalam and Hindi for the demo milestones - pending the
native-speaker review the build plan calls for).
"""

from sqlalchemy.orm import Session

from . import voice
from .models import ExplanationCache

# --- Curated fallback content (demo-critical strings; native review pending) ---

MILESTONE_EXPLANATIONS_EN = {
    "first_consultation": "Your first check-up of the pregnancy. The doctor confirms the pregnancy, notes your health history, and plans your care. Bring any old prescriptions and reports, and note the first day of your last period.",
    "baseline_bloods": "Basic blood tests done once at the start: haemoglobin, blood group, sugar, and infection screening. They catch problems early, when they are easiest to treat. No fasting needed unless your doctor says so.",
    "nt_scan": "An ultrasound between weeks 11 and 14 that measures fluid at the back of the baby's neck. It is an early check on the baby's development. A moderately full bladder helps.",
    "second_trimester_review": "A routine check of your weight, blood pressure, and the baby's growth. Mention any new symptoms, even small ones.",
    "anomaly_scan": "A detailed ultrasound, usually between weeks 18 and 22, that checks the baby's organs - heart, brain, spine, kidneys. It finds most physical differences early so the doctor can plan care. No fasting needed. It takes about 30 to 45 minutes.",
    "consultation_24w": "A routine visit to check your blood pressure and how the baby is growing. Bring your anomaly scan report.",
    "ogtt": "A sugar test for gestational diabetes. You come fasting, drink a glucose solution, and give blood samples over 2 hours. If sugar is high, diet changes and sometimes medicine protect you and the baby.",
    "tdap_vaccine": "A vaccination that protects your baby from whooping cough and tetanus in the first months of life. Safe in pregnancy.",
    "third_trimester_review": "A check of haemoglobin, blood pressure, and the baby's growth as you enter the last trimester. Mention any swelling, headache, or reduced baby movements.",
    "growth_scan": "An ultrasound that checks the baby's growth and the fluid around the baby.",
    "review_34w": "A routine late-pregnancy check. Start counting baby movements every day.",
    "review_36w": "A check-up to discuss your birth plan and warning signs.",
    "review_38w": "A late check-up. Know when to go to the hospital: regular pains, leaking water, or bleeding.",
    "birth_plan": "Finalise where you will deliver, how you will get there, and who comes with you.",
}

# Demo milestone in Malayalam and Hindi (native-speaker review pending, per plan).
MILESTONE_EXPLANATIONS_LOCAL = {
    ("anomaly_scan", "ml"): "കുഞ്ഞിന്റെ അവയവങ്ങൾ - ഹൃദയം, തലച്ചോർ, നടുവ്, വൃക്ക - വിശദമായി പരിശോധിക്കുന്ന ഒരു അൾട്രാസൗണ്ടാണ് അനോമലി സ്കാൻ (TIFFA). സാധാരണ ഗർഭത്തിന്റെ 18 മുതൽ 22 ആഴ്ച വരെയാണ് ഇത് ചെയ്യുന്നത്. ഭൂരിഭാഗം ശാരീരിക വ്യത്യാസങ്ങളും നേരത്തെ കണ്ടെത്താൻ ഇത് സഹായിക്കുന്നു. ഉപവാസം ആവശ്യമില്ല. ഏകദേശം 30 മുതൽ 45 മിനിറ്റ് വരെ എടுகും.",
    ("anomaly_scan", "hi"): "एनोमली स्कैन (TIFFA) एक विस्तृत अल्ट्रासाउंड है जिसमें बच्चे के अंग - दिल, दिमाग, रीढ़, किडनी - ध्यान से जांचे जाते हैं। यह आमतौर पर गर्भावस्था के 18 से 22 सप्ताह के बीच होता है। इससे ज़्यादातर शारीरिक समस्याएं जल्दी पकड़ी जाती हैं। उपवास की ज़रूरत नहीं है। इसमें लगभग 30 से 45 मिनट लगते हैं।",
    ("baseline_bloods", "ml"): "ഗർഭത്തിന്റെ തുടക്കത്തിൽ ഒന്ന് ചെയ്യുന്ന അടിസ്ഥാന രക്തപരിശോധനകളാണ് ഇവ: ഹീമോഗ്ലോബിൻ, രക്തഗ്രൂപ്പ്, പഞ്ചസാര, അണുബാധ പരിശോധന. പ്രശ്നങ്ങൾ നേരത്തെ കണ്ടെത്താൻ ഇത് സഹായിക്കുന്നു. ഡോക്ടർ പറഞ്ഞില്ലെങ്കിൽ ഉപവാസം ആവശ്യമില്ല.",
    ("baseline_bloods", "hi"): "ये गर्भावस्था की शुरुआत में एक बार होने वाले बुनियादी खून के टेस्ट हैं: हीमोग्लोबिन, ब्लड ग्रुप, शुगर और इन्फेक्शन की जांच। इससे समस्याएं जल्दी पकड़ी जाती हैं। डॉक्टर बोलें तो ही उपवास करें।",
}

RESULT_EXPLANATIONS_EN = {
    "hb": "Haemoglobin carries oxygen in your blood. In pregnancy it should stay at or above 11 g/dL. Lower means anaemia - common and treatable with iron-rich food and iron tablets.",
    "bp_sys": "The top blood-pressure number. It should stay below 140. Higher needs a doctor's review because it can signal pre-eclampsia.",
    "bp_dia": "The bottom blood-pressure number. It should stay below 90. Higher needs a doctor's review.",
    "glucose_fasting": "Your blood sugar before eating. It should stay below 92 mg/dL. Higher can mean gestational diabetes, managed with diet and sometimes medicine.",
    "glucose_ogtt_1h": "Your blood sugar one hour after the glucose drink. It should stay below 180 mg/dL.",
    "glucose_ogtt_2h": "Your blood sugar two hours after the glucose drink. It should stay below 153 mg/dL.",
}

PROMPT_TEMPLATE = (
    "Explain this pregnancy check-up to a mother in simple, warm words. "
    "Four short parts: what it is, why it matters, what is normal, what to do. "
    "No medical jargon, no diagnosis, under 80 words.\n"
    "Check-up: {title}. Notes for the mother: {prep_notes}"
)


def _english_milestone_text(milestone_key: str, title: str, prep_notes: str) -> tuple[str, str]:
    generated = voice.chat(PROMPT_TEMPLATE.format(title=title, prep_notes=prep_notes or "None."))
    if generated:
        return generated, "sarvam"
    curated = MILESTONE_EXPLANATIONS_EN.get(milestone_key)
    if curated:
        return curated, "curated"
    return f"{title}. {prep_notes}".strip(), "template"


def milestone_explanation(db: Session, milestone_key: str, title: str, prep_notes: str, template_version: str, language: str) -> dict:
    cache_key = f"{milestone_key}|{language}|{template_version}"
    row = db.query(ExplanationCache).filter(ExplanationCache.cache_key == cache_key).first()
    if row:
        return {"text": row.text, "language": language, "audio_available": bool(row.audio_path), "provider": row.provider, "cache": True}

    english, provider = _english_milestone_text(milestone_key, title, prep_notes)
    if language == "en":
        text = english
    else:
        text = voice.translate(english, language) or MILESTONE_EXPLANATIONS_LOCAL.get((milestone_key, language)) or english
        if text != english and not voice.available():
            provider = "curated"
    row = ExplanationCache(cache_key=cache_key, text=text, language=language, provider=provider, audio_path="")
    db.add(row)
    db.commit()
    return {"text": text, "language": language, "audio_available": False, "provider": provider, "cache": False}


def result_explanation(db: Session, code: str, label: str, template_version: str, language: str) -> dict:
    return milestone_explanation(db, f"result:{code}", label, RESULT_EXPLANATIONS_EN.get(code, ""), template_version, language)


def explanation_audio(db: Session, cache_prefix: str, language: str) -> bytes | None:
    """Bulbul v3 audio for a cached explanation; generated once and stored."""
    row = (
        db.query(ExplanationCache)
        .filter(ExplanationCache.cache_key.like(f"{cache_prefix}|{language}|%"))
        .order_by(ExplanationCache.id.desc())
        .first()
    )
    if row is None:
        return None
    if row.audio_path:
        from .storage import read_upload

        return read_upload(row.audio_path)
    audio = voice.tts(row.text, language)
    if audio is None:
        return None
    from .storage import save_upload

    row.audio_path = save_upload("audio", row.id, f"{row.id}.mp3", audio)
    db.commit()
    return audio
