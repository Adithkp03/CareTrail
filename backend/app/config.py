import os

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./caretrail.db")
TOKEN_TTL_DAYS = int(os.getenv("TOKEN_TTL_DAYS", "30"))
