-- Core schema for Milestone 1: companies, their sources, raw and normalized postings, ingestion runs.
-- Enum-like columns are varchar + CHECK (see docs/ARCHITECTURE.md, "Data model").

create table companies (
    id         bigint generated always as identity primary key,
    name       varchar(200) not null,
    slug       varchar(100) not null,
    website    varchar(500),
    active     boolean      not null default true,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint uq_companies_slug unique (slug)
);

create table company_sources (
    id          bigint generated always as identity primary key,
    company_id  bigint       not null references companies (id),
    source_type varchar(32)  not null,
    identifier  varchar(500) not null,
    region      varchar(16)  not null default 'GLOBAL',
    active      boolean      not null default true,
    created_at  timestamptz  not null default now(),
    constraint uq_company_sources_type_identifier unique (source_type, identifier),
    constraint ck_company_sources_source_type
        check (source_type in ('GREENHOUSE', 'LEVER', 'SMARTRECRUITERS', 'JSON_LD')),
    constraint ck_company_sources_region check (region in ('GLOBAL', 'EU'))
);

create index idx_company_sources_company on company_sources (company_id);

-- Full source payloads, kept internally for re-processing. An unchanged payload is stored once.
create table raw_postings (
    id                bigint generated always as identity primary key,
    company_source_id bigint       not null references company_sources (id),
    external_id       varchar(200) not null,
    payload           jsonb        not null,
    payload_hash      varchar(64)  not null,
    fetched_at        timestamptz  not null,
    constraint uq_raw_postings_source_external_hash unique (company_source_id, external_id, payload_hash)
);

create table postings (
    id                bigint generated always as identity primary key,
    company_id        bigint        not null references companies (id),
    company_source_id bigint        not null references company_sources (id),
    source_type       varchar(32)   not null,
    external_id       varchar(200)  not null,
    title             varchar(500)  not null,
    url               varchar(2000) not null,
    locations         text[]        not null default '{}',
    remote_type       varchar(16)   not null default 'UNKNOWN',
    snippet           varchar(300),
    posted_at         timestamptz,
    first_seen_at     timestamptz   not null,
    last_seen_at      timestamptz   not null,
    closed_at         timestamptz,
    status            varchar(16)   not null default 'OPEN',
    content_hash      varchar(64)   not null,
    is_early_career   boolean       not null default false,
    opportunity_type  varchar(32)   not null default 'OTHER',
    domain            varchar(32)   not null default 'OTHER',
    seniority         varchar(16)   not null default 'UNKNOWN',
    language          varchar(8),
    paid              boolean,
    -- 'simple' configuration: postings mix English and Romanian, a language stemmer would mangle the other.
    search_vector     tsvector generated always as (
                          to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(snippet, ''))
                      ) stored,
    created_at        timestamptz   not null default now(),
    updated_at        timestamptz   not null default now(),
    constraint uq_postings_source_external unique (company_source_id, external_id),
    constraint ck_postings_source_type
        check (source_type in ('GREENHOUSE', 'LEVER', 'SMARTRECRUITERS', 'JSON_LD')),
    constraint ck_postings_remote_type check (remote_type in ('ONSITE', 'HYBRID', 'REMOTE', 'UNKNOWN')),
    constraint ck_postings_status check (status in ('OPEN', 'CLOSED')),
    constraint ck_postings_opportunity_type
        check (opportunity_type in ('INTERNSHIP', 'WORKING_STUDENT', 'GRADUATE_PROGRAM', 'JUNIOR', 'OTHER')),
    constraint ck_postings_domain
        check (domain in ('SOFTWARE', 'DATA', 'ENGINEERING', 'DESIGN', 'PRODUCT', 'FINANCE', 'MARKETING',
                          'SALES', 'HR', 'LEGAL', 'OPERATIONS', 'CUSTOMER_SUPPORT', 'RESEARCH', 'OTHER')),
    constraint ck_postings_seniority
        check (seniority in ('INTERN', 'ENTRY', 'JUNIOR', 'MID', 'SENIOR', 'LEAD', 'UNKNOWN'))
);

-- Default feed: open early-career postings, newest first.
create index idx_postings_feed on postings (is_early_career, status, posted_at desc nulls last);
create index idx_postings_company on postings (company_id);
create index idx_postings_opportunity_type on postings (opportunity_type);
create index idx_postings_domain on postings (domain);
create index idx_postings_locations on postings using gin (locations);
create index idx_postings_search on postings using gin (search_vector);

create table ingestion_runs (
    id                bigint generated always as identity primary key,
    company_source_id bigint        not null references company_sources (id),
    source_type       varchar(32)   not null,
    started_at        timestamptz   not null,
    finished_at       timestamptz,
    status            varchar(16)   not null,
    fetched_count     integer       not null default 0,
    new_count         integer       not null default 0,
    updated_count     integer       not null default 0,
    closed_count      integer       not null default 0,
    error_message     varchar(4000),
    constraint ck_ingestion_runs_source_type
        check (source_type in ('GREENHOUSE', 'LEVER', 'SMARTRECRUITERS', 'JSON_LD')),
    constraint ck_ingestion_runs_status check (status in ('RUNNING', 'SUCCEEDED', 'FAILED'))
);

create index idx_ingestion_runs_source_started on ingestion_runs (company_source_id, started_at desc);
