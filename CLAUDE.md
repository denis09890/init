# CLAUDE.md — project memory for Init

Init aggregates early-career opportunities in Romania (internships, working-student, graduate/trainee,
junior roles; all fields) from public company sources. Portfolio project shown to recruiters, so code
quality, tests and documentation matter as much as features.

## Working rules (mandatory)

1. Claude owns all file changes in the repository.
2. **Claude never runs terminal commands** (no mvnw, npm, docker, java, curl, git, ...). When something
   must be run, give the exact **Windows PowerShell** commands, the folder to run them in and the
   expected output, then wait for the owner to paste the result.
3. **Claude never runs git.** At each checkpoint: say it is time to commit, list changed files, give a
   Conventional Commits message (`feat:`, `fix:`, `test:`, `docs:`, `chore:`, `refactor:`, `ci:`).
   Keep commits small.
4. Plan first, then build in small steps. After each step: summary, verification commands, commit message.
5. **Talk to the owner in Romanian.** Code, comments, commits and docs are in **English**.
6. Explain every new dependency in one line before adding it. Prefer Spring / JDK standard library.
7. Never commit secrets. Use environment variables and `.env.example`.
8. If something is ambiguous, ask instead of guessing.

Reading files and fetching public web pages/docs (to verify API formats) is fine; shell commands are not.

## Data source rules (non-negotiable)

- Only public sources meant for consumption: ATS job-board APIs (Greenhouse, Lever, SmartRecruiters,
  later Ashby, Workable, Recruitee, Personio), schema.org `JobPosting` JSON-LD, manual submissions.
- Never scrape LinkedIn, eJobs, BestJobs or sites whose terms forbid automated access.
- Respect `robots.txt` for HTML pages. User-Agent: `InitBot/0.1 (+https://github.com/denis09890/init)`.
- Public output: snippet of max ~300 chars + link to the original. Full raw payload stays internal.
- No personal data of recruiters.
- Never invent ATS identifiers. Example companies must be verified against the live public API and
  marked as examples in the config.
- Verify every endpoint and response format against the platform's official docs before implementing.

## Stack

- Backend: Java 21 (owner has JDK 25 locally; build targets release 21), Spring Boot (version in
  `backend/pom.xml`), Maven wrapper, PostgreSQL, Flyway, Spring Data JPA, `RestClient`, virtual threads.
- Tests: JUnit 5, AssertJ, Mockito, Testcontainers (real PostgreSQL).
- Frontend: React + TypeScript + Vite, React Router, Tailwind CSS.
- Infra: Docker Compose (local PostgreSQL), GitHub Actions CI.
- Approved extra dependencies: jsoup (JSON-LD extraction from HTML).

## Layout

```
backend/src/main/java/dev/init/
  company/         companies and their source configuration
  source/          connectors: Greenhouse, Lever, SmartRecruiters, JSON-LD
  ingestion/       scheduler, pipeline, runs, rate limiting, retries
  posting/         posting domain, persistence, search, REST API
  classification/  early-career / type / domain classification
  common/          shared config, errors, utilities
frontend/          React app
docs/              ARCHITECTURE.md, ROADMAP.md, adr/
```

## Java conventions

Package-by-feature; records for DTOs and value objects; constructor injection only; no Lombok; no field
injection; small focused classes; meaningful names; Bean Validation on inputs; errors as `ProblemDetail`.
Database: Flyway owns the schema (`ddl-auto: validate`); enums stored as `varchar` + `CHECK`.

## How to run and test (PowerShell)

```powershell
Copy-Item .env.example .env          # repo root, once
docker compose up -d                 # repo root
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
cd backend; .\mvnw.cmd verify        # needs Docker running (Testcontainers)
cd frontend; npm install; npm run dev
cd frontend; npm run lint; npm run build
```

## Status

Current: **Milestone 1** (see `docs/ROADMAP.md` for the step checklist).

- [x] Docs skeleton
- [ ] Owner: scaffold backend (Spring Initializr) and frontend (Vite); install Docker Desktop
- Next: step 2, infrastructure and configuration.

Verified example sources (checked against the live public APIs on 2026-11-22):
- Greenhouse `scbitdefendersrl` (Bitdefender): 54 jobs, many in Bucharest/Iasi. API host is
  `boards-api.greenhouse.io` (the `boards-api.eu` host does not resolve), but `absolute_url` points to
  `job-boards.eu.greenhouse.io`.
- Greenhouse `cresta` (Cresta): Bucharest, Cluj and Iasi roles, has run a junior SWE internship.
- SmartRecruiters `BoschGroup` (Bosch): `?country=ro` returned 90 Romanian postings, incl. working student.
- Lever: no Romanian company verified yet. Fixtures can use `leverdemo`, Lever's official demo site.

API facts that shape the connectors (from the official docs):
- Greenhouse: `GET /v1/boards/{token}/jobs?content=true`, no pagination, `{jobs, meta.total}`;
  location is free text ("Bucharest", "Bucharest, RO", "Romania"), so normalization is needed.
- Lever: `GET /v0/postings/{site}?mode=json&skip=&limit=`, global host `api.lever.co` and EU host
  `api.eu.lever.co` (source config needs a region); no country filter, use the `country` field.
- SmartRecruiters: `GET /v1/companies/{id}/postings?country=ro&offset=&limit=` (max 100),
  `{offset, limit, totalFound, content}`; the description needs a per-posting detail call
  (`/postings/{id}`), so rate limiting matters.

Decisions taken with the owner:
- Classifier is built before the ingestion pipeline (swapped steps 6 and 7 of the original brief).
- Claude finds and verifies the example companies against the public ATS APIs.
