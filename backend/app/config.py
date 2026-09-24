import os

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./caretrail.db")
# Render/Heroku-style URLs use the legacy postgres:// scheme; SQLAlchemy 2 + psycopg needs postgresql+psycopg://
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql+psycopg://", 1)
elif DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg://", 1)
TOKEN_TTL_DAYS = int(os.getenv("TOKEN_TTL_DAYS", "30"))
