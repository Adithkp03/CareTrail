"""Extraction eval (Phase 6): run the extractor over the eval set and print one
accuracy number - correct fields / expected fields. Used in tests and on stage."""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.extraction import offline_extract  # noqa: E402
from app.template_loader import load_template  # noqa: E402

TOLERANCE = 0.05


def run() -> dict:
    cases = json.loads((Path(__file__).parent / "evalset.json").read_text())
    template = load_template("antenatal", "v1")
    total = correct = 0
    misses = []
    for case in cases:
        got = {v["code"]: v["value"] for v in offline_extract(case["text"], template)}
        for code, want in case["expected"].items():
            total += 1
            actual = got.get(code)
            if actual is not None and abs(actual - want) <= TOLERANCE:
                correct += 1
            else:
                misses.append({"id": case["id"], "code": code, "want": want, "got": actual})
    accuracy = correct / total if total else 0.0
    return {"cases": len(cases), "fields": total, "correct": correct, "accuracy": round(accuracy, 4), "misses": misses}


if __name__ == "__main__":
    result = run()
    print(f"Extraction accuracy: {result['accuracy']:.1%} ({result['correct']}/{result['fields']} fields, {result['cases']} reports)")
    for m in result["misses"]:
        print("  miss:", m)
