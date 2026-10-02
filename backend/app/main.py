import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .database import Base, engine
from .migrate import apply as apply_migrations
from .routers import auth, clinicians, demo, documents, flags, i18n, journeys, milestones, prep, reminders, signoffs, trust, voice

Base.metadata.create_all(engine)
apply_migrations()

_PROD = os.getenv("VERCEL_ENV") == "production" or os.getenv("APP_ENV") == "production"
_DOCS_ON = not _PROD or os.getenv("ENABLE_DOCS") == "1"

app = FastAPI(
    title="CareTrail API", version="0.1.0",
    docs_url="/docs" if _DOCS_ON else None,
    redoc_url="/redoc" if _DOCS_ON else None,
    openapi_url="/openapi.json" if _DOCS_ON else None,
)

# Web app + Capacitor Android shell + local dev. Auth is a bearer header (no cookies),
# so this limits which sites' scripts can call the API from a browser.
_DEFAULT_ORIGINS = (
    "https://caretrail-web.vercel.app,https://localhost,capacitor://localhost,"
    "http://localhost,http://localhost:3000"
)
_ORIGINS = [o.strip() for o in os.getenv("CORS_ORIGINS", _DEFAULT_ORIGINS).split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_ORIGINS,
    allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Demo-Key"],
)


@app.middleware("http")
async def security_headers(request, call_next):
    response = await call_next(request)
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("X-Frame-Options", "DENY")
    response.headers.setdefault("Referrer-Policy", "no-referrer")
    response.headers.setdefault("Cache-Control", "no-store")
    response.headers.setdefault("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
    return response


app.include_router(auth.router)
app.include_router(journeys.router)
app.include_router(milestones.router)
app.include_router(documents.router)
app.include_router(flags.router)
app.include_router(signoffs.router)
app.include_router(clinicians.router)
app.include_router(demo.router)
app.include_router(voice.router)
app.include_router(prep.router)
app.include_router(reminders.router)
app.include_router(trust.router)
app.include_router(i18n.router)


@app.get("/health")
def health():
    return {"status": "ok", "service": "caretrail-api", "phase": 1}
