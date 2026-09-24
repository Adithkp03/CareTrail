from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .database import Base, engine
from .routers import auth, demo, documents, flags, journeys, milestones, signoffs, voice

Base.metadata.create_all(engine)

app = FastAPI(title="CareTrail API", version="0.1.0")

# Hackathon-wide CORS so the Vercel frontend can talk to the API; restrict in hardening.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(journeys.router)
app.include_router(milestones.router)
app.include_router(documents.router)
app.include_router(flags.router)
app.include_router(signoffs.router)
app.include_router(demo.router)
app.include_router(voice.router)


@app.get("/health")
def health():
    return {"status": "ok", "service": "caretrail-api", "phase": 1}
