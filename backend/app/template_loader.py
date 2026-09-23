import json
from functools import lru_cache
from pathlib import Path

PATHWAYS_DIR = Path(__file__).parent / "pathways"


@lru_cache(maxsize=8)
def load_template(template_id: str = "antenatal", version: str = "v1") -> dict:
    path = PATHWAYS_DIR / f"{template_id}_{version}.json"
    if not path.exists():
        raise ValueError(f"Unknown pathway template: {template_id} {version}")
    return json.loads(path.read_text())
