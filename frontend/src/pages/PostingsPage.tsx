import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { COMPANIES_URL, postingsUrl } from '../api/endpoints.ts'
import type { Company, Page, Posting } from '../api/types.ts'
import { useJson } from '../api/useJson.ts'
import FilterPanel from '../components/FilterPanel.tsx'
import Pagination from '../components/Pagination.tsx'
import PostingCard from '../components/PostingCard.tsx'
import { EmptyState, ErrorPanel, LoadingList } from '../components/StatusViews.tsx'
import {
  activeFilterCount,
  changeFilters,
  EMPTY_FILTERS,
  parseFilters,
  toSearchParams,
} from '../lib/filters.ts'
import type { FilterPatch } from '../lib/filters.ts'
import { DEFAULT_TITLE, useDocumentTitle } from '../lib/useDocumentTitle.ts'

export default function PostingsPage() {
  const [params, setParams] = useSearchParams()
  const filters = parseFilters(params)
  const postings = useJson<Page<Posting>>(postingsUrl(filters))
  const companies = useJson<Company[]>(COMPANIES_URL)
  const [filtersOpen, setFiltersOpen] = useState(false)
  useDocumentTitle(DEFAULT_TITLE)

  const query = params.toString()
  const from = query ? `/?${query}` : '/'
  const activeFilters = activeFilterCount(filters)
  const page = postings.data

  function update(patch: FilterPatch) {
    setParams(toSearchParams(changeFilters(filters, patch)))
  }

  function clear() {
    setParams(toSearchParams(EMPTY_FILTERS))
  }

  function goToPage(next: number) {
    setParams(toSearchParams({ ...filters, page: next }))
    window.scrollTo({ top: 0 })
  }

  function renderResults() {
    if (postings.error) {
      return <ErrorPanel error={postings.error} onRetry={postings.retry} />
    }
    if (!page) {
      return <LoadingList />
    }
    if (page.items.length === 0 && page.totalItems > 0) {
      // a bookmarked page number that no longer exists
      return (
        <div className="card space-y-3 p-8 text-center">
          <p className="text-sm text-slate-600 dark:text-slate-300">This page is empty.</p>
          <button type="button" className="btn-primary" onClick={() => goToPage(0)}>
            Go to the first page
          </button>
        </div>
      )
    }
    if (page.items.length === 0) {
      return <EmptyState filtered={activeFilters > 0} onClear={clear} />
    }
    return (
      <>
        <ul className={`space-y-4 transition-opacity ${postings.loading ? 'opacity-60' : ''}`}>
          {page.items.map((posting) => (
            <li key={posting.id}>
              <PostingCard posting={posting} from={from} />
            </li>
          ))}
        </ul>
        <Pagination page={page.page} totalPages={page.totalPages} onPage={goToPage} />
      </>
    )
  }

  return (
    <div className="md:grid md:grid-cols-[18rem_1fr] md:items-start md:gap-8">
      <aside aria-label="Filters" className="mb-6 md:sticky md:top-4 md:mb-0">
        <button
          type="button"
          className="btn-secondary w-full md:hidden"
          aria-expanded={filtersOpen}
          aria-controls="filters"
          onClick={() => setFiltersOpen((open) => !open)}
        >
          {filtersOpen ? 'Hide filters' : `Filters${activeFilters > 0 ? ` (${activeFilters})` : ''}`}
        </button>
        <div id="filters" className={`card mt-3 p-4 md:mt-0 md:block ${filtersOpen ? 'block' : 'hidden'}`}>
          <FilterPanel filters={filters} companies={companies.data ?? []} onChange={update} onClear={clear} />
        </div>
      </aside>

      <section aria-labelledby="results-heading" aria-busy={postings.loading}>
        <header className="mb-4">
          <h1 id="results-heading" className="text-2xl font-bold tracking-tight">
            Early-career opportunities in Romania
          </h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400" aria-live="polite">
            {summarize(page, postings.loading)}
          </p>
        </header>
        {renderResults()}
      </section>
    </div>
  )
}

function summarize(page: Page<Posting> | null, loading: boolean): string {
  if (!page) {
    return 'Loading postings…'
  }
  const count = `${page.totalItems.toLocaleString('en-GB')} ${page.totalItems === 1 ? 'posting' : 'postings'}`
  return loading ? `${count} · updating…` : count
}
