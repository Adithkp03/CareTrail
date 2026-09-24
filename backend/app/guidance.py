"""Guidance grounding (Phase 5). A curated corpus of FOGSI/WHO-aligned antenatal
guidance chunks. Answers retrieve from it first and cite it; when nothing matches
we say "ask your doctor" instead of guessing.

Retrieval: Gemini embeddings + cosine when GEMINI_API_KEY is set (chunk embeddings
cached in-process); otherwise a deterministic keyword-overlap scorer, so tests and
the no-key demo work offline.
"""

import json
import os
import re
from pathlib import Path

_LOCAL_CORPUS = Path(__file__).with_name("guidance_corpus.local.json")

CORPUS: list[dict] = [
    {"id": "anc-schedule", "topic": "antenatal visits", "source": "FOGSI/WHO antenatal guidance (curated summary, demo corpus - replace with licensed source text)",
     "text": "WHO recommends at least 8 antenatal contacts. Key visits: booking by 12 weeks, then around 20, 26, 30, 34, 36, 38 and 40 weeks. Each visit checks blood pressure, weight, growth and symptoms."},
    {"id": "anaemia", "topic": "haemoglobin", "source": "FOGSI/WHO antenatal guidance (curated summary, demo corpus - replace with licensed source text)",
     "text": "In pregnancy, haemoglobin below 11 g/dL is anaemia. It is treated with iron and folic acid supplements and iron-rich food (green leafy vegetables, jaggery, dates, meat). Haemoglobin is checked at booking and again around 28-30 weeks."},
    {"id": "hypertension", "topic": "blood pressure", "source": "FOGSI/WHO antenatal guidance (curated summary, demo corpus - replace with licensed source text)",
     "text": "Blood pressure of 140/90 or above in pregnancy needs medical review. With severe headache, blurred vision or swelling of face and hands it can signal pre-eclampsia - contact the doctor immediately."},
    {"id": "gdm-ogtt", "topic": "glucose", "source": "FOGSI/WHO antenatal guidance (IADPSG criteria, curated summary, demo corpus)",
     "text": "The 75 g oral glucose tolerance test is done at 24-28 weeks. Gestational diabetes is diagnosed if fasting glucose is 92 mg/dL or more, 1-hour is 180 or more, or 2-hour is 153 or more. Most cases are managed with diet and walking; some need medicine."},
    {"id": "anomaly-scan", "topic": "anomaly scan", "source": "FOGSI antenatal ultrasound guidance (curated summary, demo corpus - replace with licensed source text)",
     "text": "The anomaly scan (TIFFA) at 18-22 weeks examines the baby's organs in detail: heart, brain, spine, kidneys, limbs, and the placenta's position. It finds most structural differences early."},
    {"id": "nt-scan", "topic": "nt scan", "source": "FOGSI antenatal ultrasound guidance (curated summary, demo corpus - replace with licensed source text)",
     "text": "The nuchal translucency scan between 11 and 13+6 weeks measures fluid behind the baby's neck. Combined with blood tests it screens for chromosomal conditions."},
    {"id": "tdap", "topic": "vaccination", "source": "WHO immunization guidance (curated summary, demo corpus - replace with licensed source text)",
     "text": "Tetanus-diphtheria-pertussis (Tdap) vaccination in the third trimester passes antibodies to the baby and protects against whooping cough in the first months of life."},
    {"id": "danger-signs", "topic": "danger signs", "source": "WHO antenatal guidance (curated summary, demo corpus - replace with licensed source text)",
     "text": "Contact a doctor immediately for: vaginal bleeding, leaking fluid, severe headache or blurred vision, reduced or absent baby movements, severe abdominal pain, high fever, or swelling of face and hands with headache."},
    {"id": "nutrition", "topic": "nutrition", "source": "FOGSI/WHO antenatal nutrition guidance (curated summary, demo corpus - replace with licensed source text)",
     "text": "Eat one extra balanced meal a day, with iron-rich foods, calcium (milk, curd, ragi) and protein (dal, eggs, fish). Avoid raw papaya in excess, alcohol, tobacco and self-medication."},
    {"id": "movements", "topic": "baby movements", "source": "FOGSI/WHO antenatal guidance (curated summary, demo corpus - replace with licensed source text)",
     "text": "Most mothers feel movements by 20-24 weeks. From 28 weeks, count movements daily. Clearly fewer movements than usual needs a same-day check."},
]

# The team's real guideline chunks (scripts/load_guidance.py) take precedence when
# present; the curated demo corpus above is the fallback.
if _LOCAL_CORPUS.exists():
    try:
        CORPUS = json.loads(_LOCAL_CORPUS.read_text())
    except Exception:
        pass

_STOP = {"the", "a", "an", "is", "are", "was", "what", "why", "how", "when", "for", "and", "with", "this", "that", "your", "you", "about", "does", "mean", "my", "need", "day", "days", "week", "weeks", "time", "will", "can"}


def _tokens(text: str) -> set[str]:
    return {t for t in re.split(r"[^a-z0-9]+", text.lower()) if len(t) > 2 and t not in _STOP}


_chunk_embeddings: dict[str, list[float]] = {}


def _embed(texts: list[str]) -> list[list[float]] | None:
    if not os.getenv("GEMINI_API_KEY"):
        return None
    import httpx

    try:
        out = []
        with httpx.Client(timeout=30) as c:
            for text in texts:
                r = c.post(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent",
                    headers={"x-goog-api-key": os.environ["GEMINI_API_KEY"]},
                    json={"content": {"parts": [{"text": text}]}},
                )
                r.raise_for_status()
                out.append(r.json()["embedding"]["values"])
        return out
    except Exception:
        return None


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = sum(x * x for x in a) ** 0.5
    nb = sum(x * x for x in b) ** 0.5
    return dot / (na * nb) if na and nb else 0.0


def retrieve(question: str, k: int = 2) -> list[dict]:
    """Best-matching guidance chunks with a confidence gate. Empty means ungrounded."""
    q_tokens = _tokens(question)
    if not q_tokens:
        return []

    q_vec = (_embed([question]) or [None])[0]
    scored: list[tuple[float, dict]] = []
    for chunk in CORPUS:
        if q_vec is not None:
            if chunk["id"] not in _chunk_embeddings:
                emb = _embed([chunk["text"]])
                _chunk_embeddings[chunk["id"]] = emb[0] if emb else []
            c_vec = _chunk_embeddings[chunk["id"]]
            score = _cosine(q_vec, c_vec) if c_vec else 0.0
            threshold = 0.55
        else:
            overlap = q_tokens & _tokens(chunk["text"] + " " + chunk["topic"])
            score = len(overlap) / max(len(q_tokens), 1)
            if _tokens(chunk["topic"]) & q_tokens:
                score += 0.15  # topic words in the question are the strongest signal
            threshold = 0.25
        if score >= threshold:
            scored.append((score, chunk))

    scored.sort(key=lambda x: x[0], reverse=True)
    return [{"id": c["id"], "topic": c["topic"], "source": c["source"], "text": c["text"], "score": round(s, 3)} for s, c in scored[:k]]
