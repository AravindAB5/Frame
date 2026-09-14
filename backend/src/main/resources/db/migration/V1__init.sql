create extension if not exists pgcrypto;

create table users (
    id              uuid primary key default gen_random_uuid(),
    email           varchar(255) not null unique,
    password_hash   varchar(255) not null,
    display_name    varchar(120) not null,
    created_at      timestamptz not null default now()
);

create table videos (
    id                  uuid primary key default gen_random_uuid(),
    owner_id            uuid not null references users(id) on delete cascade,
    title               varchar(255) not null,
    original_filename   varchar(512) not null,
    storage_path        varchar(1024) not null,
    content_type        varchar(120) not null,
    size_bytes          bigint not null,
    duration_seconds    double precision,
    status              varchar(20) not null
                            check (status in ('UPLOADING','QUEUED','PROCESSING','READY','FAILED')),
    thumbnail_path      varchar(1024),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index idx_videos_owner_id on videos(owner_id);
create index idx_videos_status on videos(status);

create table processing_jobs (
    id                  uuid primary key default gen_random_uuid(),
    video_id            uuid not null references videos(id) on delete cascade,
    status              varchar(20) not null
                            check (status in ('QUEUED','RUNNING','COMPLETED','FAILED')),
    progress_percent    integer not null default 0,
    stage               varchar(120),
    error_message       text,
    started_at          timestamptz,
    completed_at        timestamptz,
    created_at          timestamptz not null default now()
);

create index idx_processing_jobs_video_id on processing_jobs(video_id);

create table events (
    id                  uuid primary key default gen_random_uuid(),
    video_id            uuid not null references videos(id) on delete cascade,
    event_type          varchar(30) not null
                            check (event_type in ('SCENE_CHANGE','CHAPTER','OCR_TEXT','AUDIO_EVENT','SPEAKER_CHANGE')),
    timestamp_ms        bigint not null,
    end_timestamp_ms    bigint,
    title               varchar(255) not null,
    description         text,
    metadata            jsonb not null default '{}'::jsonb,
    confidence          double precision,
    created_at          timestamptz not null default now()
);

create index idx_events_video_id_timestamp on events(video_id, timestamp_ms);
create index idx_events_video_id_type on events(video_id, event_type);

create table markers (
    id              uuid primary key default gen_random_uuid(),
    video_id        uuid not null references videos(id) on delete cascade,
    user_id         uuid not null references users(id) on delete cascade,
    timestamp_ms    bigint not null,
    label           varchar(255) not null,
    note            text,
    color           varchar(7),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index idx_markers_video_id_timestamp on markers(video_id, timestamp_ms);
