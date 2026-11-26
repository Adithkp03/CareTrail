-- CareTrail community room (opt-in, pseudonymous, team-moderated).
-- Tables are also auto-created by SQLAlchemy ensure_tables() on backend startup;
-- run this only for manual provisioning. Afterwards re-run
-- backend/app/sql/private_backend_tables.sql so the new tables stay off the
-- Supabase client-facing Data API (RLS enabled, anon/authenticated revoked).

create table if not exists community_members (
    patient_id text primary key references patients(id),
    nickname varchar(24) not null,
    suspended boolean not null default false,
    suspended_reason varchar(200) not null default '',
    joined_at timestamptz not null default now()
);

create table if not exists community_messages (
    id varchar(40) primary key,
    patient_id text not null references patients(id),
    nickname varchar(24) not null,
    body varchar(500) not null,
    removed boolean not null default false,
    removed_by varchar(120) not null default '',
    created_at timestamptz not null default now()
);
create index if not exists ix_community_messages_patient_id on community_messages(patient_id);
create index if not exists ix_community_messages_created_at on community_messages(created_at);

create table if not exists community_reports (
    id varchar(40) primary key,
    message_id varchar(40) not null references community_messages(id),
    reporter_patient_id text not null references patients(id),
    reason varchar(24) not null,
    detail varchar(300) not null default '',
    status varchar(16) not null default 'open',
    action varchar(24) not null default '',
    handled_by varchar(120) not null default '',
    handled_at timestamptz,
    created_at timestamptz not null default now()
);
create index if not exists ix_community_reports_message_id on community_reports(message_id);
create index if not exists ix_community_reports_reporter on community_reports(reporter_patient_id);

create table if not exists community_blocks (
    id varchar(80) primary key,
    blocker_patient_id text not null references patients(id),
    blocked_patient_id text not null references patients(id),
    created_at timestamptz not null default now()
);
create index if not exists ix_community_blocks_blocker on community_blocks(blocker_patient_id);
create index if not exists ix_community_blocks_blocked on community_blocks(blocked_patient_id);

create table if not exists community_moderators (
    token varchar(64) primary key,
    name varchar(120) not null,
    created_at timestamptz not null default now()
);
