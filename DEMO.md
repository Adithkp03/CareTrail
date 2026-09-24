# CareTrail - Demo playbook (Phase 7)

## 3-minute demo script

1. **Hook (20s)** - "An ASHA worker hands a phone to Anjali, 22 weeks pregnant in Kerala. She can't read English reports. Watch."
2. **Timeline (30s)** - Login with the demo account. Home screen shows done / now / next in Malayalam. Tap the current milestone -> plain-language "why this matters".
3. **Upload + trust (40s)** - Upload `backend/sample_reports/cbc_01.txt` (hemoglobin 10.4). The flag appears instantly: deterministic rules, not an LLM. Tap it -> explanation with "based on doctor-set thresholds (WHO/FOGSI)".
4. **Doctor loop (30s)** - Open /doctor: pre-consult brief + the same flag. Sign off as the doctor -> patient timeline shows "Dr. reviewed".
5. **Voice (30s)** - Tap Ask, speak a question in Malayalam -> answer in Malayalam with audio reply (needs Sarvam key; falls back to text without it).
6. **Trust close (20s)** - Consent screen, audit trail, "rules not LLM" for safety flags. End: "CareTrail is rails for trust, not another chatbot."

## Reset between runs
- Doctor view -> "Reset demo" button, or `curl -X POST $API/demo/seed`. Idempotent; restores Anjali (9000000001 / demo1234, 22w0d, Malayalam).

## Offline fallback
- PWA service worker (production builds) caches the app shell and read-only API GETs.
- Timeline, next-up card and milestone explanations also persist to localStorage; on network failure the last-loaded state renders with an "Offline" banner.
- Rehearse once on venue wifi so every screen the script touches is cached.

## Backup video (team task)
Record a full 3-min run on a laptop with seeded data; keep it downloaded locally. Play from file if live demo fails.

## Likely judge questions
- **What if the AI is wrong?** Safety flags are deterministic rules over extracted values; thresholds live in `pathways/antenatal_v1.json` owned by the clinician. LLM only writes plain-language explanations, never decides.
- **Privacy?** Consent-gated access, per-user accounts, audit trail (trust ledger) of every view/edit.
- **Regional languages?** Sarvam for ml/hi TTS+STT+translation; rules engine is language-agnostic.
- **Scale?** FastAPI + Postgres in prod, stateless API; rules engine runs in-process.
