# ADR 0003: Rule-based classification first

- **Status:** Accepted
- **Date:** 2026-11-22

## Context

Most postings returned by company sources are not early-career roles. Init must decide, for every
posting, whether it fits students and graduates with 0–2 years of experience, and also its opportunity
type (internship, working student, graduate program, junior), domain and language. Postings are in
English and Romanian.

An LLM would classify ambiguous postings better, but it adds cost, latency, an external dependency and
non-determinism. Without a labeled evaluation set there is also no way to prove it is better.

## Decision

Milestone 1 uses a **rule-based classifier** behind a `PostingClassifier` interface:

- Early-career detection from keywords in titles and descriptions, in English and Romanian
  (intern, internship, trainee, graduate, junior, entry level, working student, student, stagiar,
  stagiu, practică, absolvent, ...).
- Explicit exclusions that override positive matches (senior, lead, principal, manager,
  "X+ years", "minim X ani", ...).
- Opportunity type and domain from keyword groups; location relevance for Romania or remote roles open
  to Romania/EU.
- Matching is diacritic-insensitive (`practică` and `practica` are equivalent).

Every posting is stored with an `is_early_career` flag; the public API returns early-career postings by
default.

Milestone 2 adds an LLM-based classifier behind the same interface, with caching by content hash and
the rules as fallback, evaluated against a manually labeled set of postings.

## Consequences

- Classification is free, fast, deterministic and fully unit-tested from day one.
- Rules will miss ambiguous postings and produce some false positives. Keeping all postings (not only
  early-career ones) means they can be re-classified later without re-fetching.
- The rule set becomes the baseline the LLM classifier must beat on the evaluation set.
