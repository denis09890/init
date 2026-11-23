-- Full-text search must find "practică" when someone types "practica". The text-search configuration
-- 'simple' keeps diacritics, so they are removed (Romanian ones, both cedilla and comma-below forms)
-- before the text is indexed; the API removes them from the query the same way (see Diacritics).
drop index idx_postings_search;
alter table postings drop column search_vector;
alter table postings add column search_vector tsvector generated always as (
    to_tsvector('simple', lower(translate(
        coalesce(title, '') || ' ' || coalesce(snippet, ''),
        'ăâîșşțţĂÂÎȘŞȚŢ',
        'aaissttAAISSTT')))
) stored;
create index idx_postings_search on postings using gin (search_vector);
