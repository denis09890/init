# Init frontend

React 19, TypeScript, Vite and Tailwind CSS. It shows the postings that the backend collects
(see the [root README](../README.md) and [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)).

## Run

```powershell
npm install
npm run dev        # http://localhost:5173
```

The backend must be running on `http://localhost:8080`; the dev server proxies `/api` to it, so no
CORS configuration is needed.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | Type-check and production build into `dist/` |
| `npm run lint` | ESLint |
| `npm test` | Unit tests of the pure logic (`node --test`, no extra dependencies) |

## Structure

```
src/
  api/          types mirroring the API, fetch client, endpoint URLs, the useJson hook
  lib/          URL <-> filter conversion, labels, date formatting, safe external links (+ tests)
  components/   layout, filter panel, posting card, badges, pagination, loading/error/empty states
  pages/        postings list, posting detail, not found
```

## Design notes

- **The URL is the state.** Search, filters and the page number live in the query string, using the
  API's own parameter names. A search can be shared, and the back button works.
- **No data-fetching library.** `useJson` loads a URL, aborts requests that are no longer wanted
  and keeps the previous results on screen (dimmed) while the next ones load.
- **Links to other sites are checked.** The link to the original posting comes from company data,
  so it is only rendered when it is an `http(s)` address.
- **Accessible by default:** real buttons and labels, `aria-pressed` on the filter chips, a live
  region for the result count, visible focus, and a layout that works on a phone and in dark mode.
