"""Load the team's chosen guideline documents into the local guidance corpus.

Downloads the official PDFs the team picked, extracts their text, chunks it, and
writes app/guidance_corpus.local.json (gitignored - the source documents stay on
the official sites, we do not redistribute them). The demo corpus in
app/guidance.py remains the fallback. Check each document's usage terms with the
team doctor before shipping answers grounded in it.
"""

import json
import re
import sys
from pathlib import Path

SOURCES = [
    {"id": "fogsi-gcpr-anc", "name": "FOGSI-ICOG GCPR: Routine Antenatal Care for the Healthy Pregnant Woman",
     "url": "https://www.fogsi.org/wp-content/uploads/2024/08/Binder_Routine-Antenatal-Care-for-the-Healthy-Pregnant-Women.pdf"},
    {"id": "fogsi-anc-checklist", "name": "FOGSI ANC Checklist",
     "url": "https://www.fogsi.org/wp-content/uploads/2024/11/ANC_Checklist_Conclave-Final-1.pdf"},
    {"id": "pmsma-high-risk", "name": "MoHFW/PMSMA: High-Risk Conditions in Pregnancy",
     "url": "https://pmsma.mohfw.gov.in/wp-content/uploads/2016/10/High-Risk-Conditions-in-preg-modified-Final.pdf"},
    {"id": "mohfw-sba", "name": "MoHFW SBA guidelines (skilled attendance at birth)",
     "url": "https://nhm.gov.in/images/pdf/programmes/maternal-health/guidelines/sba_guidelines_for_skilled_attendance_at_birth.pdf"},
]

OUT = Path(__file__).resolve().parent.parent / "app" / "guidance_corpus.local.json"


def extract_text(url: str) -> str:
    import io

    import httpx
    from pypdf import PdfReader

    r = httpx.get(url, timeout=60, follow_redirects=True)
    r.raise_for_status()
    reader = PdfReader(io.BytesIO(r.content))
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def chunk(text: str) -> list[str]:
    paras = re.split(r"\n\s*\n", text)
    out, buf = [], ""
    for p in paras:
        p = " ".join(p.split())
        if not p:
            continue
        if len(buf) + len(p) < 600:
            buf = f"{buf} {p}".strip()
        else:
            if len(buf) > 120:
                out.append(buf)
            buf = p
    if len(buf) > 120:
        out.append(buf)
    return out


def main():
    corpus = []
    for src in SOURCES:
        print(f"Fetching {src['name']}...")
        try:
            text = extract_text(src["url"])
        except Exception as exc:
            print(f"  FAILED: {exc}")
            continue
        for i, piece in enumerate(chunk(text)):
            corpus.append({"id": f"{src['id']}-{i}", "topic": src["name"], "source": src["name"] + f" ({src['url']})", "text": piece})
        print(f"  {len(corpus)} chunks so far")
    OUT.write_text(json.dumps(corpus, indent=1))
    print(f"Wrote {len(corpus)} chunks to {OUT}")


if __name__ == "__main__":
    sys.exit(main())
