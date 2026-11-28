-- Duplicate detection (see docs/ARCHITECTURE.md, "Duplicates"). A company sometimes publishes the same
-- role twice: two requisitions with one title and one city, or one job on two platforms. The later
-- posting points to the first one and is hidden from the feed; nothing is deleted.
-- pg_trgm provides similarity() for titles that differ only slightly ("–" instead of "-").

create extension if not exists pg_trgm;

alter table postings add column duplicate_of_id bigint references postings (id) on delete set null;

create index idx_postings_duplicate_of on postings (duplicate_of_id);
