"""Report text -> proposed clinical values (Phase 3).

Routing: Malayalam/Hindi reports go through Sarvam Vision, English ones through
Gemini Flash with a strict JSON schema - but only when SARVAM_API_KEY /
GEMINI_API_KEY are set. Without keys (dev, tests, CI) a deterministic offline
extractor parses the same value patterns, including Malayalam and Hindi test
labels, so the demo and the test-suite never depend on a network call.

An LLM only proposes values here. Flags are always decided by the rules engine
(app/flags.py) against the doctor's thresholds - never by a model.
"""

import os
import re
from datetime import date

KNOWN_TEST_CODES = {
    "hb": "Haemoglobin",
    "bp_sys": "Systolic BP",
    "bp_dia": "Diastolic BP",
    "glucose_fasting": "Fasting glucose",
    "glucose_ogtt_1h": "OGTT 1-hour glucose",
    "glucose_ogtt_2h": "OGTT 2-hour glucose",
}

# Multilingual label aliases -> test code. The offline parser and the LLM prompt
# share this vocabulary.
LABEL_ALIASES = {
    "hb": ["haemoglobin", "hemoglobin", "hb", "ഹീമോഗ്ലോബിൻ", "हीमोग्लोबिन"],
    "bp": ["blood pressure", "bp", "രക്തസമ്മർദ്ദം", "रक्तचाप"],
    "glucose_fasting": ["fasting glucose", "fasting blood glucose", "fbs", "fasting blood sugar", "ഉപവാസ ഗ്ലൂക്കോസ്", "उपवास ग्लूकोज"],
    "glucose_ogtt_1h": ["1 hour", "1-hour", "1 hr", "1 മണിക്കൂർ", "1 घंटा"],
    "glucose_ogtt_2h": ["2 hour", "2-hour", "2 hr", "2 മണിക്കൂർ", "2 घंटा"],
}


def detect_language(text: str) -> str:
    """ml / hi / en from the script the report is written in."""
    malayalam = sum(1 for c in text if "\u0d00" <= c <= "\u0d7f")
    devanagari = sum(1 for c in text if "\u0900" <= c <= "\u097f")
    if malayalam > 5:
        return "ml"
    if devanagari > 5:
        return "hi"
    return "en"


def normalize_value(code: str, value: float, unit: str) -> tuple[float, str]:
    """Bring units to the ones the doctor's thresholds use."""
    u = unit.strip().lower()
    if code == "hb":
        if u in ("g/l", "g l-1"):
            return round(value / 10.0, 1), "g/dL"
        return value, "g/dL"
    if code.startswith("glucose"):
        if u in ("mmol/l", "mmol l-1"):
            return round(value * 18.0, 1), "mg/dL"
        return value, "mg/dL"
    if code.startswith("bp"):
        return value, "mmHg"
    return value, unit


def _find_number_near(text: str, aliases: list[str]) -> tuple[float, str] | None:
    for alias in aliases:
        m = re.search(re.escape(alias) + r"[^\d]{0,25}(\d+(?:\.\d+)?)\s*(g/dL|g/L|mmol/L|mg/dL)?", text, re.IGNORECASE)
        if m:
            return float(m.group(1)), (m.group(2) or "")
    return None


def _find_bp(text: str) -> tuple[int, int] | None:
    for alias in LABEL_ALIASES["bp"]:
        m = re.search(re.escape(alias) + r"[^\d]{0,25}(\d{2,3})\s*/\s*(\d{2,3})", text, re.IGNORECASE)
        if m:
            return int(m.group(1)), int(m.group(2))
    return None


def _find_date(text: str) -> date | None:
    m = re.search(r"(20\d{2})-(\d{2})-(\d{2})", text)
    if m:
        return date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
    m = re.search(r"\b(\d{1,2})/(\d{1,2})/(20\d{2})\b", text)
    if m:
        return date(int(m.group(3)), int(m.group(2)), int(m.group(1)))
    return None


def offline_extract(text: str, template: dict) -> list[dict]:
    """Deterministic parser used when no API keys are configured. Understands the
    shared label vocabulary in English, Malayalam and Hindi."""
    thresholds = template.get("thresholds", {})
    observed_on = _find_date(text) or date.today()
    found: list[dict] = []

    bp = _find_bp(text)
    if bp:
        for code, val in (("bp_sys", bp[0]), ("bp_dia", bp[1])):
            found.append({"code": code, "value": float(val), "unit": "mmHg", "confidence": 0.9})

    single_codes = ("hb", "glucose_fasting", "glucose_ogtt_1h", "glucose_ogtt_2h")
    for code in single_codes:
        hit = _find_number_near(text, LABEL_ALIASES[code])
        if hit is None:
            continue
        value, unit = hit
        if not unit:
            # Unit missing: accept the number but mark it for review.
            value, unit = normalize_value(code, value, "g/dL" if code == "hb" else "mg/dL")
            confidence = 0.55
        else:
            value, unit = normalize_value(code, value, unit)
            confidence = 0.9
        found.append({"code": code, "value": value, "unit": unit, "confidence": confidence})

    out = []
    for item in found:
        rule = thresholds.get(item["code"], {})
        ref = " / ".join(
            f"{k} {v} {rule.get('unit', item['unit'])}" for k, v in (("min", rule.get("min")), ("max", rule.get("max"))) if v is not None
        )
        out.append(
            {
                "code": item["code"],
                "label": KNOWN_TEST_CODES[item["code"]],
                "value": item["value"],
                "unit": item["unit"],
                "reference_range": ref,
                "observed_on": observed_on.isoformat(),
                "confidence": item["confidence"],
            }
        )
    return out


def provider_extract(text: str, language: str, template: dict) -> tuple[list[dict] | None, str]:
    """Real AI extraction. Malayalam/Hindi -> Sarvam Vision, English -> Gemini Flash
    with a strict JSON schema. Returns (values, provider) or (None, reason) so the
    caller can fall back to the offline parser."""
    import json

    import httpx

    schema_hint = (
        'Return ONLY a JSON array of objects: {"code","value","unit","observed_on","confidence"}. '
        f"Allowed codes: {sorted(KNOWN_TEST_CODES)}. Dates as YYYY-MM-DD. No prose."
    )
    try:
        if language in ("ml", "hi") and os.getenv("SARVAM_API_KEY"):
            resp = httpx.post(
                "https://api.sarvam.ai/vision",
                headers={"api-subscription-key": os.environ["SARVAM_API_KEY"]},
                json={"input": text, "prompt": "Extract lab test values. " + schema_hint},
                timeout=30,
            )
            resp.raise_for_status()
            data = resp.json()
            raw = data.get("output") or data.get("text") or "[]"
            return json.loads(raw), "sarvam-vision"
        if language == "en" and os.getenv("GEMINI_API_KEY"):
            body = {
                "contents": [{"parts": [{"text": "Extract lab test values from this report.\n" + schema_hint + "\n\n" + text}]}],
                "generationConfig": {"responseMimeType": "application/json"},
            }
            resp = None
            # key goes in a header so it never shows up in URLs or logs;
            # fall through to a lighter model when one is overloaded (503/429)
            for model in ("gemini-flash-latest", "gemini-flash-lite-latest"):
                resp = httpx.post(
                    f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
                    headers={"x-goog-api-key": os.environ["GEMINI_API_KEY"]},
                    json=body,
                    timeout=30,
                )
                if resp.status_code not in (404, 429, 500, 503):
                    break
            resp.raise_for_status()
            raw = resp.json()["candidates"][0]["content"]["parts"][0]["text"]
            parsed = json.loads(raw)
            if isinstance(parsed, dict):
                parsed = parsed.get("values") or parsed.get("results") or next((v for v in parsed.values() if isinstance(v, list)), [])
            return parsed, "gemini-flash"
    except Exception as exc:  # keep the offline fallback, but leave a trace in the logs
        resp = getattr(exc, "response", None)
        status = getattr(resp, "status_code", "")
        detail = (getattr(resp, "text", "") or "")[:200]
        print(f"[extraction] provider error: {type(exc).__name__} {status} {detail}")
        return None, "provider-error"
    return None, "no-key"


def extract_values(text: str, language: str, template: dict) -> tuple[list[dict], str]:
    """Try the routed provider first; fall back to the offline parser."""
    values, provider = provider_extract(text, language, template)
    if values:
        thresholds = template.get("thresholds", {})
        out = []
        for v in values:
            code = v.get("code")
            if code not in KNOWN_TEST_CODES or v.get("value") is None:
                continue
            value, unit = normalize_value(code, float(v["value"]), v.get("unit") or "")
            rule = thresholds.get(code, {})
            ref = " / ".join(
                f"{k} {x} {rule.get('unit', unit)}" for k, x in (("min", rule.get("min")), ("max", rule.get("max"))) if x is not None
            )
            out.append(
                {
                    "code": code,
                    "label": KNOWN_TEST_CODES[code],
                    "value": value,
                    "unit": unit,
                    "reference_range": ref,
                    "observed_on": v.get("observed_on") or date.today().isoformat(),
                    "confidence": float(v.get("confidence") or 0.6),
                }
            )
        return out, provider
    return offline_extract(text, template), "offline-parser"
