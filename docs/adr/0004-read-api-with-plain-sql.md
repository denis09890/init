# ADR 0004: Read API with plain SQL instead of JPA queries

- **Status:** Accepted
- **Date:** 2026-11-23

## Context

The postings API filters on many optional parameters, searches text and returns flat response
objects that mix columns of two tables (a posting and its company). Three of its needs go beyond what
JPA expresses comfortably:

- **Full-text search** over a generated `tsvector` column (`@@`, `plainto_tsquery`, `ts_rank`).
- **Array functions**: the city filter looks into the `locations text[]` column with `unnest`.
- **Search that ignores Romanian diacritics**, so that `practica` finds `Practică`.

JPA Specifications or the Criteria API can reach these only through custom function registrations, and
the result would be harder to read than the SQL it generates. Entities are also the wrong shape for
the response: they carry lifecycle fields, lazy associations and no company name.

## Decision

- The **write side** (ingestion) keeps using JPA entities and repositories.
- The **read side** (`PostingSearchRepository`, `CompanyQueryRepository`) uses Spring's `JdbcClient`
  with SQL, and maps rows straight to response records.
- Dynamic filters are built from **fixed SQL fragments** chosen by code; every value reaches the
  database as a **bound parameter**. Nothing from the request is concatenated into the SQL.
- Diacritics are removed in two places that must agree: the generated `search_vector` column
  (`translate`, migration V3) and the query text (`Diacritics` in Java). A test sends the 14 characters
  through both and compares the result.
- No `unaccent` extension: `translate` is built in, and the set of characters is small and known.

## Consequences

- The queries are short, readable and use PostgreSQL features directly.
- SQL is not checked at compile time, so every filter combination is tested against a real
  PostgreSQL (Testcontainers), including ranking, ordering and paging.
- The read side is tied to PostgreSQL. That is accepted: the schema already uses `jsonb`, arrays and
  generated columns.
- Only Romanian diacritics are handled, and there is no stemming (`internship` does not find
  `internships`). A proper text-search configuration per language is a Milestone 2 candidate.
