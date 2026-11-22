# Test fixtures

Responses of the public job-board APIs, used by the connector tests.

- Recorded on 2026-11-22 from the live public endpoints named in each connector's Javadoc.
- **Trimmed** to a few postings, and **descriptions replaced** with short placeholder text: full job
  descriptions are never republished in this repository (see
  `docs/adr/0002-data-sources-and-legal-stance.md`).
- Field names, nesting and value formats are kept exactly as returned. Ids, titles, locations, dates
  and URLs are taken from the recordings; a few secondary values (some metadata, departments, list
  items) were simplified.
- `jsonld/` contains a hand-written career page following the schema.org `JobPosting` vocabulary,
  for a fictional company on the reserved `example.com` domain.

To refresh a fixture, record the endpoint again, then trim it the same way.
