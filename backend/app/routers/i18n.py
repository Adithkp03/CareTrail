"""Batch translation of backend-provided content (milestone titles, prep notes,
danger signs, next-up tips, flag messages) so every screen reads in the chosen
language. Each string is translated once per language and cached."""

import hashlib
from concurrent.futures import ThreadPoolExecutor

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from .. import voice
from ..database import get_db
from ..deps import get_current_patient
from ..i18n_curated import curated
from ..models import ExplanationCache, Patient

router = APIRouter(tags=["i18n"])

MAX_TEXTS = 80
MAX_CHARS = 1500


class TranslateRequest(BaseModel):
    texts: list[str]
    lang: str


def _key(text: str, lang: str) -> str:
    return f"tr|{lang}|{hashlib.sha1(text.encode('utf-8')).hexdigest()}"


def _translate_one(text: str, lang: str) -> str | None:
    try:
        return voice.translate(text, lang)
    except Exception as exc:  # one bad string should not break the screen
        print(f"[i18n] translate failed: {type(exc).__name__}")
        return None


@router.post("/i18n/translate")
def translate_batch(body: TranslateRequest, patient: Patient = Depends(get_current_patient), db: Session = Depends(get_db)):
    if body.lang not in ("en", "ml", "hi"):
        raise HTTPException(status_code=400, detail="lang must be en, ml or hi")
    texts = [t for t in dict.fromkeys(body.texts) if isinstance(t, str) and t.strip()][:MAX_TEXTS]
    if body.lang == "en" or not texts:
        return {"lang": body.lang, "translations": {t: t for t in texts}}

    out: dict[str, str] = {}
    for t in texts:
        c = curated(t, body.lang)
        if c:
            out[t] = c
    texts = [t for t in texts if t not in out]
    keys = {t: _key(t, body.lang) for t in texts}
    rows = db.query(ExplanationCache).filter(ExplanationCache.cache_key.in_(list(keys.values()))).all()
    cached = {r.cache_key: r.text for r in rows}
    missing = []
    for t in texts:
        if keys[t] in cached:
            out[t] = cached[keys[t]]
        else:
            missing.append(t)

    if missing and voice.available():
        with ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(lambda s: _translate_one(s[:MAX_CHARS], body.lang), missing))
        for t, tr in zip(missing, results):
            if tr:
                out[t] = tr
                db.add(ExplanationCache(cache_key=keys[t], text=tr, language=body.lang, provider="sarvam-translate", audio_path=""))
        db.commit()
    for t in body.texts:
        out.setdefault(t, t)  # fall back to English for anything that failed
    return {"lang": body.lang, "translations": out}
