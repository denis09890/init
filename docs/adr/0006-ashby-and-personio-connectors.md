# ADR 0006: Ashby and Personio connectors; Workable and Recruitee not now

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

Greenhouse, Lever and SmartRecruiters cover few employers in Romania: of 162 company names checked
with `scripts/check-ats-sources.ps1`, only 9 had boards with postings in Romania. To see what else is
used, the careers pages of about 50 companies with Romanian offices were read by hand (company
websites only, no job aggregators). They link to:

- **Ashby** (UiPath, n8n, Gen Digital), **SmartRecruiters** (Nagarro);
- **Recruitee** (Evozon), **iCIMS** (Arobs), **Oracle Taleo** (Societe Generale), **Workday** (Adobe);
- **their own career sites** (Bolt, Glovo, Luxoft, Cegeka, Grid Dynamics, Qubiz, msg systems, Centric
  and others).

ADR 0002 allows only sources intended for public consumption, verified against the platform's own
documentation and a live response.

## Decision

Add two connectors:

- **Ashby**, through its documented public job posting API
  (`GET https://api.ashbyhq.com/posting-api/job-board/{board}`): no authentication, every published job
  in one response. Jobs marked `isListed: false` are meant to be reached by direct link only and are
  skipped.
- **Personio**, through its documented public XML feed (`GET https://{company}.jobs.personio.de/xml`):
  no authentication, every published position in one document. The feed is parsed with the JDK
  (DTDs refused, so external entities cannot be read) and each position is stored as JSON, because
  `raw_postings` holds JSON. The feed has no link per position, so the documented page pattern
  `https://{company}.jobs.personio.de/job/{id}` is used.

Not now:

- **Workable**: the documented public endpoint (`/api/accounts/{subdomain}`) redirected to a widget URL
  that answered 404 on 2026-11-28, including for the account used in Workable's own documentation.
  It cannot be verified, so it is not implemented.
- **Recruitee**: its documentation states that, from 10 February 2027, the Careers Site API requires
  a token that each company generates for its own account. Without the company's cooperation it is
  not a public source, and a connector would stop working within weeks.
- **Workday, iCIMS, Taleo, SuccessFactors**: no documented public API for third parties; the JSON
  behind their career pages serves their own web pages, which ADR 0002 rules out.
- **Own career sites**: covered case by case by the JSON-LD connector when the page publishes
  schema.org `JobPosting` data and `robots.txt` allows it.

## Consequences

- UiPath and n8n (Ashby) join the catalog; Personio has no verified company in Romania yet, and the
  lookup script now checks both platforms for every candidate.
- Like Greenhouse, both platforms return a company's postings worldwide; postings outside Romania are
  stored and filtered out of the feed by `location_eligible`.
- Ashby gives countries as names; only Romania is mapped to a country code, and secondary locations
  are kept because a remote role lists there the countries it is open to.
- If Workable's public endpoint answers again, or Recruitee keeps a tokenless mode, this decision can
  be revisited; the connector contract makes either one a single new class.
