-- Two new source types: the Ashby public job posting API and the Personio public job feed
-- (docs/adr/0006-ashby-and-personio-connectors.md). Enum-like columns are varchar + CHECK, so adding a
-- value means replacing the three constraints that list the source types.

alter table company_sources drop constraint ck_company_sources_source_type;
alter table company_sources add constraint ck_company_sources_source_type
    check (source_type in ('GREENHOUSE', 'LEVER', 'SMARTRECRUITERS', 'JSON_LD', 'ASHBY', 'PERSONIO'));

alter table postings drop constraint ck_postings_source_type;
alter table postings add constraint ck_postings_source_type
    check (source_type in ('GREENHOUSE', 'LEVER', 'SMARTRECRUITERS', 'JSON_LD', 'ASHBY', 'PERSONIO'));

alter table ingestion_runs drop constraint ck_ingestion_runs_source_type;
alter table ingestion_runs add constraint ck_ingestion_runs_source_type
    check (source_type in ('GREENHOUSE', 'LEVER', 'SMARTRECRUITERS', 'JSON_LD', 'ASHBY', 'PERSONIO'));
