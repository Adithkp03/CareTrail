import uuid
from datetime import date, datetime, timezone

from sqlalchemy import JSON, Boolean, Date, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


def new_id() -> str:
    return uuid.uuid4().hex


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Patient(Base):
    """One login account = one mother. All journey data hangs off this row."""

    __tablename__ = "patients"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=new_id)
    name: Mapped[str] = mapped_column(String(120))
    phone: Mapped[str] = mapped_column(String(32), unique=True, index=True)
    language: Mapped[str] = mapped_column(String(8), default="en")  # en / ml / hi ...
    password_hash: Mapped[str] = mapped_column(String(200))
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False)
    consent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    supabase_id: Mapped[str | None] = mapped_column(String(64), nullable=True, unique=True, index=True)
    email: Mapped[str | None] = mapped_column(String(200), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    journeys: Mapped[list["Journey"]] = relationship(back_populates="patient")


class AuthToken(Base):
    """Hackathon bearer-token auth. Swap for Supabase Auth (phone OTP) in the hardening phase."""

    __tablename__ = "auth_tokens"

    token: Mapped[str] = mapped_column(String(64), primary_key=True)
    patient_id: Mapped[str] = mapped_column(ForeignKey("patients.id"), index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class Journey(Base):
    """One pregnancy, generated from a pathway template. A patient can have several over time."""

    __tablename__ = "journeys"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=new_id)
    patient_id: Mapped[str] = mapped_column(ForeignKey("patients.id"), index=True)
    template_id: Mapped[str] = mapped_column(String(40), default="antenatal")
    template_version: Mapped[str] = mapped_column(String(20), default="v1")
    lmp: Mapped[date | None] = mapped_column(Date, nullable=True)  # last menstrual period
    edd: Mapped[date | None] = mapped_column(Date, nullable=True)  # expected delivery date
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    patient: Mapped[Patient] = relationship(back_populates="journeys")
    milestones: Mapped[list["Milestone"]] = relationship(
        back_populates="journey", order_by="Milestone.sort_order"
    )


class Milestone(Base):
    """Status is never stored: the engine derives it from completed_at / scheduled_date /
    the template window / current gestational age, so it stays correct as time passes."""

    __tablename__ = "milestones"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=new_id)
    journey_id: Mapped[str] = mapped_column(ForeignKey("journeys.id"), index=True)
    key: Mapped[str] = mapped_column(String(60))
    title: Mapped[str] = mapped_column(String(200))
    type: Mapped[str] = mapped_column(String(20))  # visit | scan | test | vaccination | review
    window_start_weeks: Mapped[float] = mapped_column(Float)
    window_end_weeks: Mapped[float] = mapped_column(Float)
    required_tests: Mapped[list] = mapped_column(JSON, default=list)
    prep_notes: Mapped[str] = mapped_column(Text, default="")
    sort_order: Mapped[int] = mapped_column()
    completed_at: Mapped[date | None] = mapped_column(Date, nullable=True)
    scheduled_date: Mapped[date | None] = mapped_column(Date, nullable=True)

    journey: Mapped[Journey] = relationship(back_populates="milestones")
    signoffs: Mapped[list["SignOff"]] = relationship(back_populates="milestone")


class Document(Base):
    """An uploaded report. Value extraction is phase 3; phase 1 stores and links it."""

    __tablename__ = "documents"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=new_id)
    journey_id: Mapped[str] = mapped_column(ForeignKey("journeys.id"), index=True)
    milestone_id: Mapped[str | None] = mapped_column(ForeignKey("milestones.id"), nullable=True)
    filename: Mapped[str] = mapped_column(String(200))
    content_type: Mapped[str] = mapped_column(String(100), default="application/pdf")
    storage_path: Mapped[str] = mapped_column(String(400), default="")
    language: Mapped[str | None] = mapped_column(String(8), nullable=True)
    status: Mapped[str] = mapped_column(String(20), default="stored")  # stored | extracting | extracted
    uploaded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Observation(Base):
    """A single clinical value (haemoglobin, blood pressure, glucose...)."""

    __tablename__ = "observations"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=new_id)
    journey_id: Mapped[str] = mapped_column(ForeignKey("journeys.id"), index=True)
    milestone_id: Mapped[str | None] = mapped_column(ForeignKey("milestones.id"), nullable=True)
    document_id: Mapped[str | None] = mapped_column(ForeignKey("documents.id"), nullable=True)
    code: Mapped[str] = mapped_column(String(40))  # hb | bp_sys | bp_dia | glucose_fasting | ...
    value: Mapped[float] = mapped_column(Float)
    unit: Mapped[str] = mapped_column(String(30))
    observed_on: Mapped[date] = mapped_column(Date)
    source: Mapped[str] = mapped_column(String(20), default="manual")  # manual | extracted | seed


class SignOff(Base):
    """The Path B trust loop: a doctor's review written onto the mother's timeline."""

    __tablename__ = "signoffs"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=new_id)
    journey_id: Mapped[str] = mapped_column(ForeignKey("journeys.id"), index=True)
    milestone_id: Mapped[str | None] = mapped_column(ForeignKey("milestones.id"), nullable=True)
    observation_id: Mapped[str | None] = mapped_column(ForeignKey("observations.id"), nullable=True)
    doctor_name: Mapped[str] = mapped_column(String(120))
    note: Mapped[str] = mapped_column(Text, default="")
    signed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    milestone: Mapped[Milestone | None] = relationship(back_populates="signoffs")


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=new_id)
    actor: Mapped[str] = mapped_column(String(160))  # patient:<id> or doctor:<name>
    action: Mapped[str] = mapped_column(String(60))
    entity: Mapped[str] = mapped_column(String(40))
    entity_id: Mapped[str] = mapped_column(String(40))
    detail: Mapped[dict] = mapped_column(JSON, default=dict)
    at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class ExplanationCache(Base):
    """Generated explanation text (and its audio), cached by milestone/result key +
    language + template version so each string is generated once, not per page load."""

    __tablename__ = "explanation_cache"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=new_id)
    cache_key: Mapped[str] = mapped_column(String(120), unique=True, index=True)
    text: Mapped[str] = mapped_column(Text)
    language: Mapped[str] = mapped_column(String(8), default="en")
    provider: Mapped[str] = mapped_column(String(20), default="curated")
    audio_path: Mapped[str] = mapped_column(String(400), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class AiCallTrace(Base):
    """One row per AI provider call: prompt, output, latency. The eval/trust story."""

    __tablename__ = "ai_call_traces"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=new_id)
    provider: Mapped[str] = mapped_column(String(30))
    kind: Mapped[str] = mapped_column(String(30))  # chat | translate | tts | stt | vision | embed
    prompt: Mapped[str] = mapped_column(Text, default="")
    output: Mapped[str] = mapped_column(Text, default="")
    status: Mapped[str] = mapped_column(String(10), default="ok")
    latency_ms: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
