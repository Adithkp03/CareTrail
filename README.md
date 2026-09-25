# CareTrail

CareTrail is a maternal-health journey prototype built for the Vanquishers hackathon team. It shows a patient her pregnancy timeline, next visits, report values requiring review, and plain-language guidance. It is **not** a diagnostic tool or a substitute for a clinician. Use only synthetic data in demos until clinical, security and retention reviews are complete.

## What works in this release

- Patient phone/password signup and login with consent; a one-click synthetic Anjali demo. The timeline derives gestational age, 14 antenatal milestones, done/now/upcoming/next states, overdue labels and a next-up card from the versioned pathway.
- Upload a text report, PDF with selectable text, or a photo. Extraction proposes tracked values; the patient can edit/select them before confirmation. Confirmed values appear on the journey. The latest value per test code is compared with configured thresholds, and an out-of-range value creates a review flag. This is **not a diagnosis**.
- New sign-off requires a provisioned clinician account and an explicit patient grant to that journey. No clinician account is provisioned for this demo, so the sign-off flow is unavailable until a real clinician is verified and provisioned. Historical typed-name sign-offs remain labelled unverified.
- English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali and Marathi UI options. Some server-provided text may remain English if translation is unavailable. New-language clinical text is machine-translated and needs native-speaker and doctor review. The Sarvam Listen route produced live audio in all five newly added languages on the deployed synthetic demo. Voice Ask/STT has only mock-route tests, not real clips.
- A local-only movement note counter. It is not a fetal assessment, target count, or clinical record. Reduced or absent movements warrant prompt medical advice.
- A web/PWA frontend, FastAPI backend and Capacitor Android debug APK workflow. Offline is limited to previously loaded GET/timeline data and static assets; writes are not queued or synced.

Source entry points: `frontend/app/`, `backend/app/`, `backend/app/pathways/antenatal_v1.json`, `backend/tests/`, `.github/workflows/android-apk.yml`. Earlier phases and evolving decisions live in [the development log](docs/development-log.md).

## Live deployment and what was actually checked

The web production URL is https://caretrail-web.vercel.app/ and the API production alias used by that web build is https://caretrail-adith-k-ps-projects.vercel.app/ (`/health`). Production was last verified on September 25, 2026 from commit `297617a`; this document describes the pending deployment. Vercel is **not connected to GitHub for automatic deployments**. Merging a PR alone does not update production. `render.yaml` is not a live Render deployment. The backend is also aliased as `caretrail-two.vercel.app`.

Verified on the live synthetic demo: web login/reset, journey load (Anjali at 22 weeks, 3 of 14 milestones done), eight-language picker, and actual Sarvam Listen WAV output for Tamil, Telugu, Kannada, Bengali and Marathi. Main's Android APK build passed in [GitHub Actions](https://github.com/Adithkp03/CareTrail/actions/runs/36140032950). Not verified end to end on production: photo upload through confirmation, flags and sign-off; real Ask voice/STT; installation and use on a real Android phone. The APK API target is baked in during CI via `APK_API_URL`, and its effective value has not been independently verified here. A green build is not proof it points to the live API.

## Run locally

Requires Python 3.10+ and Node 22. From the repository root:

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

In another terminal:

```bash
cd frontend
npm ci
NEXT_PUBLIC_API_URL=http://localhost:8000 npm run dev
```

Open `http://localhost:3000/login` and click "Try the demo (Anjali)". That button resets the **synthetic** demo patient; do not use it as a real patient's workspace. For a persistent deployment, set `DATABASE_URL` to a PostgreSQL connection string. Local development uses SQLite when it is unset. Configure `SARVAM_API_KEY` and `GEMINI_API_KEY` on the server only if you need provider calls; do not commit keys. Phone/password auth works without Supabase; optional Supabase sign-in requires the relevant Supabase variables. See [deployment setup](README-deploy.md) for background, but treat the live URLs and checks above as the current runtime record.

Tests and local frontend build:

```bash
cd backend && python3 -m pytest -q
cd ../frontend && npm ci && npm run build
```

Before deployment of these changes, 89 backend tests and the frontend production build passed locally. Unit/mocked tests do not prove third-party provider output or medical correctness. The debug APK is downloadable from the Android APK workflow artifact; it is sideload-only, not a signed Play Store release. Set `APK_API_URL` to a phone-reachable deployed API before building, then verify the value in the built artifact and on a real device.

## Known limitations and safety work

1. **Privacy:** AI trace records retain only provider metadata, and `/ai-traces` filters by patient. Legacy trace text is cleared on service startup. Text redaction before a cloud call remains incomplete, especially for images and unusual report layouts. Do not use real health data until the full data flow is independently reviewed.
2. **Clinician trust:** new sign-offs require a provisioned clinician account and patient grant. None is provisioned for this demo; historical typed-name records are not verified clinician reviews.
3. **Reports:** original new upload bytes are stored in the app database alongside the report row; older `/tmp` uploads may be unavailable and require re-upload. Patients must compare each proposed value, unit and date with the original before confirmation. Live image-provider accuracy, ambiguous values, duplicate reports and retention/deletion remain unverified.
4. **Clinical review:** threshold units, flag messages, escalation wording, translated guidance, and source corpus need doctor/native-speaker review. The fallback guidance corpus is a demo summary, not a traceable, licensed guideline collection; generated text can be over-specific. A threshold breach is not a diagnosis.
5. **Timeline/offline:** overdue and scheduling basics have unit tests, but changed LMP/EDD, rescheduling, pathway revisions and disconnected-write conflicts are not covered. Offline shows a cached timeline with a banner, not queued edits. Cache isolation across accounts needs review.
6. **Operations:** there is no tested notification/reminder delivery, privacy-safe failure monitoring, or independently verified Android target/device run. Production deployment is manual.

## Data safety and scope (prototype)

CareTrail is not an electronic medical record or emergency service. Patient
accounts hold pregnancy dates, uploaded reports, extracted results, and account
details. The frontend caches the last timeline in browser local storage for
offline viewing. Cloud AI services may process report text, images, and questions.
Text masking covers phone numbers, emails, and some name lines; it does not
reliably remove every identifier, especially from images or unusual report
formats. New AI traces retain provider, kind, status and latency metadata only,
and `/ai-traces` is patient-scoped. Legacy trace text is cleared on service
startup, but historic copies or provider-side processing cannot be ruled out.
**Do not use real patient reports in this demo.** New report bytes are stored
in the application database; older local `/tmp` uploads may be lost. This
prototype lacks a tested retention or deletion workflow. It does not promise
that lab identity can never be disclosed. Do not use it as a backup or
production care system.

## Clinical review queue and future ideas

The doctor's wording suggestions remain review-pending, including NT at 11
weeks to 13+6, first-trimester beta-hCG/PAPP-A screening, optional costly PlGF,
warning signs, pregnancy registration, and a birth-kit reminder. All clinical
wording and translations need clinician and native-speaker review before release.
Exact pregnancy-specific TFT thresholds, units, gestational context, and
interpretation are pending from the doctor; no TFT threshold or flag has been
added. The NT window change applies to existing journeys through current pathway
lookup, and explanation caches are keyed to the changed notes, so older cached wording is not reused.

Future ideas, **not available now**: (1) organize scanned reports and
prescriptions by date in separate folders for clinic visits; (2) a referral
network of government health professionals for continuity of care after a move.
Neither should imply that records are shared with professionals today.
