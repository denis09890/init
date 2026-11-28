-- Skills a posting mentions (docs/ARCHITECTURE.md, "Skills"), extracted at ingestion from the title and
-- the full description. Existing postings get theirs on the next ingestion.

alter table postings add column skills text[] not null default '{}';

create index idx_postings_skills on postings using gin (skills);
