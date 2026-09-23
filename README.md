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
