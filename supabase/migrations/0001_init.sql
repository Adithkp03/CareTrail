-- CareTrail phase 1 schema (Supabase / Postgres).
-- The SQLAlchemy models in backend/app/models.py create these automatically for local
-- dev; run this in the Supabase SQL editor to provision the hosted database.
-- Row-level security is intentionally deferred: it lands with Supabase Auth (phone OTP)
-- in the hardening phase. Until then the FastAPI layer enforces per-patient ownership.

create table if not exists patients (
    id text primary key,
    name varchar(120) not null,
    phone varchar(32) not null unique,
    language varchar(8) not null default 'en',
    password_hash varchar(200) not null,
    is_demo boolean not null default false,
    created_at timestamptz not null default now()
);

create table if not exists auth_tokens (
    token varchar(64) primary key,
    patient_id text not null references patients(id),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);
create index if not exists ix_auth_tokens_patient_id on auth_tokens(patient_id);

create table if not exists journeys (
    id text primary key,
    patient_id text not null references patients(id),
    template_id varchar(40) not null default 'antenatal',
    template_version varchar(20) not null default 'v1',
    lmp date,
    edd date,
    created_at timestamptz not null default now()
);
create index if not exists ix_journeys_patient_id on journeys(patient_id);

create table if not exists milestones (
    id text primary key,
    journey_id text not null references journeys(id),
    key varchar(60) not null,
    title varchar(200) not null,
    type varchar(20) not null,
    window_start_weeks double precision not null,
    window_end_weeks double precision not null,
    required_tests jsonb not null default '[]',
    prep_notes text not null default '',
    sort_order integer not null,
    completed_at date,
    scheduled_date date
);
create index if not exists ix_milestones_journey_id on milestones(journey_id);

create table if not exists documents (
    id text primary key,
    journey_id text not null references journeys(id),
    milestone_id text references milestones(id),
    filename varchar(200) not null,
    content_type varchar(100) not null default 'application/pdf',
    storage_path varchar(400) not null default '',
    language varchar(8),
    status varchar(20) not null default 'stored',
    uploaded_at timestamptz not null default now()
);
create index if not exists ix_documents_journey_id on documents(journey_id);

create table if not exists observations (
    id text primary key,
    journey_id text not null references journeys(id),
    milestone_id text references milestones(id),
    document_id text references documents(id),
    code varchar(40) not null,
    value double precision not null,
    unit varchar(30) not null,
    observed_on date not null,
    source varchar(20) not null default 'manual'
);
create index if not exists ix_observations_journey_id on observations(journey_id);

create table if not exists signoffs (
    id text primary key,
    journey_id text not null references journeys(id),
    milestone_id text references milestones(id),
    observation_id text references observations(id),
    doctor_name varchar(120) not null,
    note text not null default '',
    signed_at timestamptz not null default now()
);
create index if not exists ix_signoffs_journey_id on signoffs(journey_id);

create table if not exists audit_logs (
    id text primary key,
    actor varchar(160) not null,
    action varchar(60) not null,
    entity varchar(40) not null,
    entity_id varchar(40) not null,
    detail jsonb not null default '{}',
    at timestamptz not null default now()
);
