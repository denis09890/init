import { toSearchParams } from '../lib/filters.ts'
import type { SearchFilters } from '../lib/filters.ts'

export const COMPANIES_URL = '/api/companies'

export function postingsUrl(filters: SearchFilters): string {
  const query = toSearchParams(filters).toString()
  return query ? `/api/postings?${query}` : '/api/postings'
}

export function postingUrl(id: string): string {
  return `/api/postings/${encodeURIComponent(id)}`
}
