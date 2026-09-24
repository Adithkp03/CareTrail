# Deploying CareTrail (Vercel + Supabase - both free, no card)

Two free accounts, about 15 minutes. Render also works (see render.yaml) but asks for a card.

## 1. Supabase (database + auth)

1. Sign up at https://supabase.com with GitHub (free, no card).
2. New project: name `caretrail`, any database password (save it), closest region.
3. Get the values (Project Settings):
   - **Database -> Connection string -> Transaction pooler** (port 6543): looks like
     `postgresql://postgres.xxxx:[password]@aws-0-...pooler.supabase.com:6543/postgres`
   - **API -> JWT Secret** and **anon public key**, plus the **Project URL** (`https://xxxx.supabase.co`).
4. Auth: email sign-in is on by default. For the demo, Supabase's built-in email sender is enough
   (a few emails/hour). Phone OTP needs a paid SMS provider (Twilio etc.) - skip it for the hackathon;
   the app's phone+password login still works alongside, so the demo account is unaffected.

## 2. Vercel - backend API

1. Sign up at https://vercel.com with GitHub (Hobby, free, no card).
2. Add New -> Project -> import `Adithkp03/CareTrail`. Root directory: leave as repo root.
3. Environment variables:
   - `DATABASE_URL` = the Supabase pooler string from step 1.3 (with the password filled in)
   - `SUPABASE_JWT_SECRET` = the JWT secret from step 1.3
   - `SARVAM_API_KEY`, `GEMINI_API_KEY` - later, when you have them (app runs without)
4. Deploy. Your API URL: `https://<project>.vercel.app` - check `/health` returns ok.
5. Seed the demo: `curl -X POST https://<project>.vercel.app/demo/seed`

Serverless notes:
- Never skip `DATABASE_URL` here: without it the API falls back to a scratch database that resets
  between instances (fine only for a quick smoke test).
- Uploaded report bytes are written to short-lived scratch space during the request; extracted
  values live in Postgres and survive. Fine for the demo.
- Data path stays backend-owned: the app talks to Postgres only through the FastAPI service
  (service connection), so row-level security policies are a hardening-phase item, not needed now.

## 3. Vercel - frontend (optional but recommended)

1. Add New -> Project -> import the same repo again. Root directory: `frontend`.
2. Environment variables:
   - `NEXT_PUBLIC_API_URL` = your backend URL from step 2
   - `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY` = from step 1.3
     (enables the "sign in with email" box on the login page)
3. Deploy -> public web URL for judges, works on any phone browser.

## 4. APK

Once the API URL is stable: set the `APK_API_URL` repo variable and re-run the Android APK
workflow - the APK then talks to the hosted backend from any phone.
