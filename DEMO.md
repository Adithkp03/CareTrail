# CareTrail - Demo playbook (Phase 7)

## 3-minute synthetic patient story

1. Start with Anjali, a synthetic patient at 22 weeks. The journey-first home shows 3/14 milestones done, "You are here" in the second trimester, and the next step. CareTrail is a multilingual pregnancy companion, not a product for one region.
2. Follow the ordered journey from completed first consultation, baseline blood tests and NT scan to the current review, scheduled anomaly scan and later OGTT. Open "Why now?" on a milestone to see its time window and preparation.
3. Upload the **synthetic** low-haemoglobin CBC from `backend/app/sample_reports/cbc_malayalam_low_hb.txt`. Link it to the second-trimester review. Review the proposed 10.2 g/dL value before confirming; the rules engine flags it for follow-up. The upload does not mark the milestone complete.
4. Open the prototype clinician review flow. Show the pre-consult brief and the flag. A named sign-off in this build is entered within the patient session, **not** separately authenticated clinician verification. Use a clearly labelled synthetic doctor name in the demo, not a real clinician endorsement.
5. Return to the patient journey; show the recorded review and the evidence-backed attention card. Explain that AI extracts/translates/explains while code computes milestones and threshold flags.
6. Close with "Know where you are. Know what's next. Know what has been reviewed." Do not imply medical clearance from an empty queue.

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
