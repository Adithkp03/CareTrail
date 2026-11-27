"""Optional generative wording for STATIC app-guide answers (chat upgrade).

Hard boundary, same as assistant.py: this module is only called for
app-help/navigation questions whose answer is a fixed app-guide text, AFTER
the deterministic danger-sign and personal-medical gates have run. It never
receives symptoms, medicines, doses, journey values, appointment dates or
report contents - only the (PII-stripped) question and the fixed guide text
it must stay within. Data-driven answers (next appointment, reminder counts,
report lists) never reach a model; they stay templated verbatim.

Off unless ASSISTANT_APPHELP_GENERATIVE=1 AND GROQ_API_KEY is set. Every
failure returns None and the deterministic guide text is used instead.

Minimal data by construction: the user's question is NEVER sent. The intent
was already chosen deterministically by assistant.py; the model receives only
the fixed guide text and rewords it, so nothing the user typed - medical,
personal or otherwise - can reach the provider.

Provider: Groq. Verified 2026-11-27 from the source pages:
- Services Agreement 4.2 (https://console.groq.com/docs/legal/services-agreement):
  Groq may not train or fine-tune on inputs/outputs without explicit customer
  permission, on any tier; a zero-data-retention setting exists.
- Billing FAQs (https://console.groq.com/docs/billing-faqs): a payment method
  is only required when upgrading to the Developer tier - the Free tier needs
  no card.
Gemini's free tier was rejected for this surface: ai.google.dev/gemini-api/terms
says Unpaid Services content is used to improve Google products and human
reviewers may read it.
"""

import os

GROQ_BASE = "https://api.groq.com/openai/v1"
GROQ_MODEL = os.getenv("GROQ_CHAT_MODEL", "llama-3.3-70b-versatile")

SYSTEM = (
    "You are CareTrail's app helper for a pregnancy-care app. Answer ONLY from "
    "the guide text below - it is the complete truth about what the app does. "
    "Never add medical information, never mention symptoms, medicines, doses or "
    "test results, and never invent app features. If the guide does not cover "
    "the question, say you cannot help with that and suggest asking their "
    "doctor through the app. Friendly, plain language, under 80 words."
)


def enabled() -> bool:
    return os.getenv("ASSISTANT_APPHELP_GENERATIVE") == "1" and bool(os.getenv("GROQ_API_KEY"))


def rephrase_app_help(guide_text: str) -> str | None:
    """Model-written wording of a fixed app guide. None when off or on any error.

    Only the static guide text is sent - never the user's question."""
    if not enabled():
        return None
    import httpx

    try:
        with httpx.Client(
            base_url=GROQ_BASE,
            headers={"Authorization": f"Bearer {os.environ['GROQ_API_KEY']}", "Content-Type": "application/json"},
            timeout=30,
        ) as c:
            r = c.post(
                "/chat/completions",
                json={
                    "model": GROQ_MODEL,
                    "messages": [
                        {"role": "system", "content": SYSTEM + "\n\nGuide text:\n" + guide_text},
                        {"role": "user", "content": "Reword this app guide for the user:\n" + guide_text},
                    ],
                    "temperature": 0.3,
                    "max_tokens": 220,
                },
            )
            r.raise_for_status()
            out = (r.json()["choices"][0]["message"].get("content") or "").strip()
            return out or None
    except Exception:
        return None
