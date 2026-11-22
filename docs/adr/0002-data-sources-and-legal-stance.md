# ADR 0002: Data sources and legal stance

- **Status:** Accepted
- **Date:** 2026-11-22

## Context

Init's value depends on collecting job postings from many companies. Some collection methods are
legally and ethically questionable: scraping job boards whose terms forbid automated access,
republishing full job descriptions, or harvesting recruiter contact details. A portfolio project must
be beyond reproach on these points.

Many companies publish their openings through applicant tracking systems (ATS) that expose
**public job-board APIs** designed for exactly this purpose: embedding and syndicating the company's
own job list. Many career pages also embed **schema.org `JobPosting` structured data** so that search
engines can index their jobs.

## Decision

Init uses only sources intended for public consumption:

1. **Public ATS job-board APIs**, one connector per platform, configured per company. Starting with
   Greenhouse, Lever and SmartRecruiters; later Ashby, Workable, Recruitee and Personio. Each endpoint
   and response format is verified against the platform's official public documentation before
   implementation.
2. **schema.org `JobPosting` JSON-LD** embedded in company career pages, through one generic parser.
3. **Manual submissions** of links by users or companies, reviewed by an admin (later milestone).

Rules:

- Never scrape LinkedIn, eJobs, BestJobs or any site whose terms forbid automated access.
- For HTML pages, respect `robots.txt` and identify the crawler with a descriptive User-Agent:
  `InitBot/0.1 (+https://github.com/denis09890/init)`.
- Publicly display only a short snippet (max ~300 characters) and a link to the original posting.
  Full payloads are kept internally only, for re-processing.
- Do not store personal data of recruiters.
- Company and ATS identifiers are never guessed; each configured source is verified against the live
  public API.
- Fetching is rate limited per host, with timeouts and backoff.

## Consequences

- Coverage is limited to companies that use a supported ATS or publish structured data. This is
  accepted; the connector design makes adding a platform a single class.
- Data quality is high: ATS APIs return structured, authoritative data straight from the employer.
- Users always apply on the company's own site, so traffic goes back to the source.
- Storing raw payloads internally allows re-processing when normalization or classification improves,
  without fetching again.
