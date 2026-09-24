"""PII stripping (Phase 6). Text going to a cloud model passes through here first.
Phone numbers, emails and "Name:"-style identifiers are masked. Presidio is used
when installed; the regex fallback always runs and is what the tests exercise."""

import re

PHONE_RE = re.compile(r"(?:\+91[\s-]?)?\d{10}|\+?\d[\d\s-]{8,}\d")
EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
NAME_LINE_RE = re.compile(r"(?im)^\s*(name|patient name|patient|പേര്|नाम|मरीज़ का नाम)\s*[:：]\s*.+$")


def strip_pii(text: str) -> str:
    try:
        from presidio_analyzer import AnalyzerEngine  # type: ignore
        from presidio_anonymizer import AnonymizerEngine  # type: ignore

        analyzer = AnalyzerEngine()
        results = analyzer.analyze(text=text, entities=["PHONE_NUMBER", "EMAIL_ADDRESS", "PERSON"], language="en")
        return AnonymizerEngine().anonymize(text=text, analyzer_results=results).text
    except ImportError:
        pass
    out = PHONE_RE.sub("[phone]", text)
    out = EMAIL_RE.sub("[email]", out)
    out = NAME_LINE_RE.sub("[name]", out)
    return out
