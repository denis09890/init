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
- `ashby/` and `personio/` were written by hand on 2026-11-28 for a fictional company named `example`,
  following the field names, nesting and value formats of the live Ashby API and Personio feed
  checked that day (including an unlisted Ashby job and an older Personio date format).

To refresh a fixture, record the endpoint again, then trim it the same way.
