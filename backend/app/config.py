import os

# Serverless filesystems are read-only outside /tmp: fall back to a /tmp SQLite
# when no DATABASE_URL is set (degraded - set Neon DATABASE_URL for persistence).
_default_db = "sqlite:////tmp/caretrail.db" if os.getenv("VERCEL") else "sqlite:///./caretrail.db"
DATABASE_URL = os.getenv("DATABASE_URL", _default_db)
# Render/Heroku-style URLs use the legacy postgres:// scheme; SQLAlchemy 2 + psycopg needs postgresql+psycopg://
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql+psycopg://", 1)
elif DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg://", 1)
TOKEN_TTL_DAYS = int(os.getenv("TOKEN_TTL_DAYS", "30"))
SUPABASE_JWT_SECRET = os.getenv("SUPABASE_JWT_SECRET", "")
