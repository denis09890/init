-- Whether a posting is in Romania, or remote and open to Romania/EU (decided by the classifier).
-- Postings outside Romania are still stored, so they can be re-evaluated without fetching again.
alter table postings add column location_eligible boolean not null default true;

-- The default feed: early-career, location-eligible, open postings, newest first.
drop index idx_postings_feed;
create index idx_postings_feed
    on postings (is_early_career, location_eligible, status, posted_at desc nulls last);
