# ADR 0001: Modular monolith

- **Status:** Accepted
- **Date:** 2026-11-22

## Context

Init needs an ingestion pipeline, a classifier, a search API and, later, user features such as alerts
and an application tracker. It is built and operated by one developer, runs on a single small
deployment, and its data fits comfortably in one PostgreSQL database.

Microservices would add network calls between components, distributed transactions or eventual
consistency, separate deployments and CI pipelines, and service discovery. None of these solve a
problem Init has today.

## Decision

Build Init as a **modular monolith**: one Spring Boot application, packaged by feature
(`company`, `source`, `ingestion`, `classification`, `posting`, `common`), with explicit dependency
rules between modules (documented in [ARCHITECTURE.md](../ARCHITECTURE.md)).

- Modules communicate through plain Java interfaces, not HTTP.
- Each module owns its tables; other modules go through its services rather than its repositories.
- Connectors and the classifier are pure (no persistence), which keeps them easy to test and to move.

## Consequences

- One build, one deployment, one database: simple local setup and CI, cheap hosting.
- Transactions stay local; ingestion can upsert postings and record runs atomically.
- Boundaries are enforced by convention and code review, not by the network. If they erode, the code
  becomes a tangled monolith; the dependency rules exist to prevent that.
- If a module later needs to scale independently (for example, ingestion), its clear boundary makes
  extracting it a contained refactoring rather than a rewrite.
