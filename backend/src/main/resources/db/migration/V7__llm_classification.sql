-- The content hash a posting had when the LLM classifier last judged it (docs/adr/0007-llm-classifier.md).
-- Equal to content_hash: the stored classification is the LLM's. Null or different: the rules' one,
-- and the posting waits for the next LLM pass. This is the cache by content hash: an unchanged posting
-- is never sent again.

alter table postings add column llm_content_hash varchar(64);
