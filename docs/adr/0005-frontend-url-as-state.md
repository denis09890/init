# ADR 0005: Frontend state lives in the URL, without a data-fetching library

- **Status:** Accepted
- **Date:** 2026-11-24

## Context

The first version of the frontend has two read-only screens: a list of postings with search, filters
and paging, and the detail of one posting. The things a user chooses (search text, filters, page) are
exactly what someone would want to share as a link or return to with the back button.

State libraries (Redux, Zustand) and data-fetching libraries (TanStack Query, SWR) solve problems this
app does not have yet: shared mutable client state, caching across screens, optimistic updates.

## Decision

- **The URL query string is the single source of truth for the filters.** It uses the same parameter
  names as the API, so one function (`toSearchParams`) builds both the browser URL and the API
  request. Unknown or repeated values in the URL are dropped, never an error.
- **Data loading is one small hook, `useJson`.** It aborts requests that are no longer wanted, ignores
  outcomes of old requests and keeps the previous results on screen while the next ones load.
- Text inputs are uncontrolled and applied on submit, so typing does not fire a request per key
  stroke and no debouncing code is needed.
- The pure logic (URL <-> filters, date formatting, link validation) sits in `src/lib`, free of React,
  and is tested with Node's built-in test runner without adding a test library.

## Consequences

- A search can be shared, bookmarked and reopened, and the back button works, with no extra code.
- There is no response cache: going back to a page loads it again. For two read-only screens over a
  fast local API this is invisible.
- When Milestone 3 adds accounts, saved searches and an application tracker, there will be writes,
  invalidation and shared state. That is the point to adopt a data-fetching library (TanStack Query
  is the likely choice) and replace `useJson`; the API layer (`src/api`) is small enough to swap.
