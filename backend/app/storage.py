"""Where uploaded report bytes live. Local disk for the hackathon build; Supabase
Storage in production (set STORAGE_DIR, or swap this module)."""

import os
from pathlib import Path

_DEFAULT_DIR = "/tmp/caretrail-storage" if os.getenv("VERCEL") else str(Path(__file__).resolve().parent.parent / "storage")
STORAGE_DIR = Path(os.getenv("STORAGE_DIR", _DEFAULT_DIR))


def save_upload(journey_id: str, document_id: str, filename: str, data: bytes) -> str:
    safe_name = Path(filename).name.replace("/", "_")[:150]
    folder = STORAGE_DIR / journey_id
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{document_id}_{safe_name}"
    path.write_bytes(data)
    return str(path)


def read_upload(storage_path: str) -> bytes:
    return Path(storage_path).read_bytes()
