# Roadmap

## Milestone 1 — Foundation and ingestion (in progress)

- [x] 1. Documentation skeleton: README, architecture, roadmap, ADRs
- [x] 0. Scaffold: Spring Boot backend (Spring Initializr) and React + TypeScript frontend (Vite)
- [x] 2. Infrastructure: Docker Compose with PostgreSQL, Spring profiles (`dev`, `test`),
      environment-based configuration, `.env.example`, root `.gitignore`
- [x] 2b. GitHub Actions CI for backend and frontend (moved early so every pull request is checked)
- [x] 3. Flyway migrations and JPA mapping for the data model
- [x] 4. Company and source configuration from YAML, upserted at startup, with verified example companies
- [x] 5. Connectors with unit tests on recorded fixtures
  - [x] Connector contract and shared HTTP client
  - [x] Greenhouse
  - [x] Lever
  - [x] SmartRecruiters
  - [x] Generic schema.org JSON-LD (with `robots.txt` support)
- [x] 6. Rule-based classifier with English and Romanian test cases
- [x] 7. Ingestion pipeline with Testcontainers integration tests
  - [x] Raw storage, normalization, upsert, NEW / UPDATED / CLOSED detection
  - [x] Ingestion runs and per-source error isolation
  - [x] Parallel fetch on virtual threads, per-host rate limiting, retries with backoff
  - [x] Scheduler
- [x] 8. REST API: postings search and detail, companies, dev-only ingestion trigger, health
- [x] 9. Frontend: postings feed with filters and search, detail view
- [ ] 10. CI hardening: branch protection on `main`, status badge in the README
- [ ] 11. End-to-end check on a fresh clone, documentation pass

**Definition of done:** all tests pass locally and in CI; a fresh clone runs with the README
instructions; ingestion of the example companies works end to end and the postings appear in the
frontend.

## Milestone 2 — Intelligence

- [ ] LLM-based classifier behind `PostingClassifier` (structured output, caching by content hash,
      fallback to rules)
- [ ] Manually labeled evaluation set and accuracy report (rules vs LLM)
- [ ] Cross-source deduplication (canonical URL + title similarity with `pg_trgm`)
- [ ] Skills extraction

## Milestone 3 — Users

- [ ] Accounts and profiles (field of study, year, skills, preferred cities)
- [ ] Match score with an explanation
- [ ] Saved searches and email alerts (outbox pattern)
- [ ] Application tracker (Saved → Applied → Interview → Offer)
- [ ] Deadline calendar and "Hiring now" section
- [ ] Cloud deployment with a public link

## Milestone 4 — Growth

- [ ] Telegram bot for alerts
- [ ] Company pages with hiring history
- [ ] Public market insights (most requested skills per field and city, trends)
- [ ] Manual submissions with admin review
- [ ] More connectors: Ashby, Workable, Recruitee, Personio
- [ ] Source health monitoring and alerting
