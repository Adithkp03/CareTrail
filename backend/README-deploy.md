# Deploying the backend (Render free tier)

One-time setup (about 5 minutes, no card needed):

1. Open https://dashboard.render.com/blueprint/new?repo=https://github.com/Adithkp03/CareTrail
2. Sign in with GitHub (creates the free Render account) and approve repo access.
3. Click Apply. Render builds the API and a free Postgres database from render.yaml.
4. When the deploy finishes, your API URL is https://caretrail-api.onrender.com (check the dashboard for the exact name).
5. Verify: open https://<your-url>/health -> {"status": "ok"}, then POST /demo/seed.

Notes:
- Free web services sleep after 15 idle minutes; the first request after sleep takes ~30-60s. Ping /health before the demo.
- Free Postgres expires after 30 days unless you click to keep it - fine for the hackathon, upgrade later if needed.
- Uploaded report files are stored on the service disk, which wipes on redeploy; extracted values live in Postgres and survive. Good enough for the demo.
- Add SARVAM_API_KEY / GEMINI_API_KEY later in the Render dashboard (Environment tab). The app runs without them using offline fallbacks.

After the API is live: set the APK_API_URL repo variable to the URL and re-run the Android APK workflow so the APK talks to the hosted backend from any phone.
