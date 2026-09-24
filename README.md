# CareTrail

Every mother sees her whole pregnancy journey - what is done, what is now, what is next - in her own language, with her doctor's sign-off visible on the timeline.

Hackathon track: Maternal & Child Health (FOGSI), patient-focused, Patient Health Journey Progress, entry path B (needs a doctor).

This repo currently contains **Phase 1: data model and journey engine** (backend).

## What phase 1 delivers

- **Patient accounts, not one hardcoded mother.** Signup/login with phone + password (bearer tokens). Every journey belongs to a patient; one patient's data is invisible to another (404, not 403).
- **Journeys from a template, not hardcoded milestones.** A patient creates a journey from her LMP or EDD and the antenatal pathway template generates every milestone. When the child-health template lands later, the same engine runs it.
- **The journey engine.** Given LMP/EDD it computes gestational age and places each milestone: `done` (completed), `now` (in its window, or overdue), `upcoming` (a booked appointment), `next` (window not open yet). Status is derived, never stored, so it stays correct as time passes.
- **The FOGSI/WHO-aligned antenatal pathway** as versioned JSON (`backend/app/pathways/antenatal_v1.json`): visits, scans, tests and vaccinations with gestational-age windows, required tests, prep notes, doctor-set thresholds (haemoglobin, blood pressure, glucose) and the danger-sign list.
- **Rules-based flags.** Values are compared to the template thresholds in plain Python. No LLM ever decides a flag.
- **The Path B trust loop.** A doctor's sign-off (milestone or flagged value) is written as an event and appears as a badge on the mother's timeline.
- **Audit log** of every mutation.
- **Synthetic demo seed only.** Anjali (27, 22 weeks, Malayalam-speaking) exists only through `POST /demo/seed` and the demo account - real patients never see her.

## API

| Method | Path | What it does |
|---|---|---|
| POST | `/auth/signup` | Create a patient account, returns a bearer token |
| POST | `/auth/login` | Log in |
| GET | `/auth/me` | Current patient |
| POST | `/journeys` | Create a journey from `lmp` or `edd` (auth) |
| GET | `/journeys` | List my journeys (auth) |
| GET | `/journey/{id}` | Full timeline: GA, milestone statuses, next appointment, flags, danger signs |
| GET | `/milestones/{id}` | Milestone detail with documents, observations, sign-off |
| POST | `/milestones/{id}/complete` | Mark done (writes audit log) |
| POST | `/milestones/{id}/schedule` | Book an appointment/scan date |
| POST | `/documents` | Register an uploaded report (extraction is phase 3) |
| GET | `/flags?journey_id=` | Threshold breaches, computed from observations |
| POST | `/signoffs` | Doctor sign-off (`X-Doctor-Name` header) onto the timeline |
| POST | `/demo/seed` | Create/reset the synthetic demo mother (no auth, demo account only) |
| GET | `/health` | Liveness |

## Run it

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
# seed the demo mother:
curl -X POST http://localhost:8000/demo/seed
# log in as her: phone 9000000001, password demo1234
```

Local dev uses SQLite by default. For Supabase, set `DATABASE_URL` to the project's
Postgres URI (see `backend/.env.example`) and apply `supabase/migrations/0001_init.sql`
in the SQL editor. The same code runs on both; ownership is enforced in the API for the
hackathon, and Postgres row-level security lands with Supabase Auth (phone OTP) in the
hardening phase (phase 8 of the build plan).

## Test

```bash
cd backend
python -m pytest
```

19 tests cover the engine (GA math, status placement, overdue), auth, journey creation,
cross-patient isolation, the Anjali seed matching the example line for line,
complete/schedule flows, threshold flags, and the sign-off + audit trail.

## Stack and layout

FastAPI + SQLAlchemy 2 (Supabase/Postgres in prod, SQLite locally). No LLM calls in
this phase by design.

```
backend/
  app/
    main.py            app wiring, CORS
    models.py          Patient, AuthToken, Journey, Milestone, Document, Observation, SignOff, AuditLog
    engine.py          gestational age + Done/Now/Upcoming/Next placement (pure functions)
    flags.py           threshold rules engine
    pathways/          versioned pathway templates (antenatal_v1.json)
    routers/           auth, journeys, milestones, documents, flags, signoffs, demo
    seed.py            synthetic Anjali (also runnable: python -m app.seed)
  tests/               pytest suite
supabase/
  migrations/0001_init.sql
```

Next: phase 2 (timeline UI) consumes `GET /journey/{id}`; phase 3 adds report upload and
extraction through Sarvam Vision / Gemini, writing Observation rows this API already serves.

## Android app (APK)

The frontend is wrapped with Capacitor (`frontend/android`, app id `com.caretrail.app`):
the Next.js app is statically exported (`next build` -> `out/`) and embedded in the shell.

**Get the APK from CI:** every push to `main` that touches `frontend/` (or a manual
"Android APK (debug)" run from the Actions tab) builds `app-debug.apk` and uploads it as
the `caretrail-debug-apk` artifact. Download, copy to the phone, allow "install unknown
apps", install. Debug build - sideloading only, no Play Store signing.

**The phone must be able to reach the backend.** The API URL is baked in at build time:
set the repo variable `APK_API_URL` (Settings > Secrets and variables > Actions) to your
laptop's LAN IP while testing at home (`http://192.168.x.x:8000`), or to the hosted API
once the backend is deployed (Render/Railway, per the build plan - the hosting step).
`http://localhost:8000` works on your laptop but never from a phone.

**Build locally instead:** needs Android Studio / Android SDK. `cd frontend && npm run build && npx cap sync && npx cap open android`, then Build > Build APK.
## Phase 3: reports in, flags out

Upload a lab report and its values land on the mother's timeline, with a flag if
anything is outside the doctor's thresholds.

Flow: `POST /journeys/{id}/documents/upload` (multipart) stores the file,
`POST /documents/{id}/extract` proposes values without saving them, the app shows an
"Is this right?" card, and `POST /documents/{id}/confirm` writes the approved values
as Observations. Flags come from the rules engine (`app/flags.py`) comparing values
to the pathway template thresholds - an LLM never decides a flag.

Extraction routing (`app/extraction.py`): Malayalam/Hindi reports go to Sarvam
Vision, English ones to Gemini Flash with a strict JSON schema - when
`SARVAM_API_KEY` / `GEMINI_API_KEY` are set in `backend/.env` (never committed).
Without keys, a deterministic offline parser reads the same value patterns,
including Malayalam and Hindi test labels, so the demo and tests work anywhere.
Units are normalised (mmol/L -> mg/dL, g/L -> g/dL) before thresholds are applied.

Five synthetic demo reports live in `backend/app/sample_reports/` - including a
Malayalam CBC with low haemoglobin (10.2 g/dL) that raises the anaemia flag, and a
Hindi note with high blood pressure. Upload them from the app's upload screen.

Tests: `cd backend && pip install -r requirements.txt && python3 -m pytest tests/ -q`
(33 tests, including the full upload -> extract -> confirm -> flag path and
cross-patient access checks).

## Phase 4: language and voice layer (Sarvam)

Everything the mother sees can be read or heard in her language, and she can ask
questions by voice.

- `GET /milestones/{id}/explanation?lang=` - plain-language explanation (what it is,
  why it matters, what's normal, what to do). Generated once and cached per
  milestone + language + template version (`explanation_cache` table) - never
  regenerated per page load. With `SARVAM_API_KEY` the text comes from Sarvam and is
  translated with Sarvam Translate; without keys, curated content (English for all
  milestones, Malayalam/Hindi for the demo ones - native-speaker review pending).
- `GET /milestones/{id}/explanation/audio?lang=` - Bulbul v3 speech, generated once
  and stored. 503 without the Sarvam key.
- `POST /ask` - text question. The doctor's danger-sign list is matched IN CODE and
  always returns "contact your doctor now"; other questions are answered from her
  own timeline (flags) and milestone grounding - no grounding means "ask your
  doctor", never a guess.
- `POST /ask/voice` - audio question: Saarika v2.5 transcribes (code-mixed speech
  works), the same grounded pipeline answers, Bulbul speaks the answer back.
  503 without the Sarvam key; the text Ask works with zero keys.

Milestone page: explanation card plus working Listen and Ask buttons (voice Ask
records in the browser). Tests: 45 passing.
