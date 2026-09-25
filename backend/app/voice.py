"""Sarvam provider wrappers (Phase 4). Everything degrades gracefully when
SARVAM_API_KEY is not set: text falls back to curated content, and audio/STT
report themselves unavailable instead of failing weirdly."""

import base64
import os

from .privacy import strip_pii
from .tracing import trace as _trace

SARVAM_BASE = "https://api.sarvam.ai"
CHAT_MODEL = os.getenv("SARVAM_CHAT_MODEL", "sarvam-105b")
TTS_MODEL = os.getenv("SARVAM_TTS_MODEL", "bulbul:v3")
# bulbul:v3 voices (anushka etc. were v2-only and now return 400)
TTS_SPEAKER = os.getenv("SARVAM_TTS_SPEAKER", "priya")
STT_MODEL = os.getenv("SARVAM_STT_MODEL", "saarika:v2.5")
TRANSLATE_MODEL = os.getenv("SARVAM_TRANSLATE_MODEL", "mayura:v1")

LANG_NAMES = {"en": "en-IN", "ml": "ml-IN", "hi": "hi-IN", "ta": "ta-IN", "te": "te-IN", "kn": "kn-IN", "bn": "bn-IN", "mr": "mr-IN"}


def available() -> bool:
    return bool(os.getenv("SARVAM_API_KEY"))


def _client():
    import httpx

    return httpx.Client(
        base_url=SARVAM_BASE,
        headers={"api-subscription-key": os.environ["SARVAM_API_KEY"], "Content-Type": "application/json"},
        timeout=45,
    )


def chat(prompt: str, db=None) -> str | None:
    """Sarvam-105B / sarvam-m text generation. None when unavailable."""
    if not available():
        return None
    safe_prompt = strip_pii(prompt)
    with _client() as c:
        r = c.post("/v1/chat/completions", json={"model": CHAT_MODEL, "messages": [{"role": "user", "content": safe_prompt}]})
        r.raise_for_status()
        msg = r.json()["choices"][0]["message"]
        out = (msg.get("content") or "").strip()
        # sarvam-105b is a reasoning model; drop any inline thinking block
        if "</think>" in out:
            out = out.split("</think>", 1)[1].strip()
        if not out:
            return None
    if db is not None:
        with _trace(db, "sarvam", "chat", safe_prompt) as t:
            t.finish(out)
    return out


def translate(text: str, target_language: str, source_language: str = "en", db=None) -> str | None:
    """Sarvam Translate. None when unavailable."""
    if not available():
        return None
    text = strip_pii(text)
    with _client() as c:
        r = c.post(
            "/translate",
            json={
                "input": text,
                "source_language_code": LANG_NAMES[source_language],
                "target_language_code": LANG_NAMES[target_language],
                "model": TRANSLATE_MODEL,
            },
        )
        r.raise_for_status()
        return r.json()["translated_text"].strip()


def tts(text: str, language: str) -> bytes | None:
    """Bulbul v3 text-to-speech -> mp3 bytes. None when unavailable."""
    if not available():
        return None
    with _client() as c:
        r = c.post(
            "/text-to-speech",
            json={
                "inputs": [text],
                "target_language_code": LANG_NAMES[language],
                "model": TTS_MODEL,
                "speaker": TTS_SPEAKER,
            },
        )
        r.raise_for_status()
        audios = r.json().get("audios") or []
        if not audios:
            return None
        return base64.b64decode(audios[0])


def stt(audio_bytes: bytes, filename: str, language: str) -> str | None:
    """Saarika v2.5 speech-to-text (handles code-mixed speech). None when unavailable."""
    if not available():
        return None
    import httpx

    with httpx.Client(base_url=SARVAM_BASE, headers={"api-subscription-key": os.environ["SARVAM_API_KEY"]}, timeout=60) as c:
        r = c.post(
            "/speech-to-text",
            files={"file": (filename, audio_bytes)},
            data={"model": STT_MODEL, "language_code": LANG_NAMES.get(language, "unknown")},
        )
        r.raise_for_status()
        return r.json().get("transcript", "").strip()
