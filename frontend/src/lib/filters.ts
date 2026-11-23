import { DOMAINS, OPPORTUNITY_TYPES, REMOTE_TYPES } from '../api/types.ts'
import type { Domain, OpportunityType, RemoteType } from '../api/types.ts'

export const LANGUAGES = ['en', 'ro'] as const
export type Language = (typeof LANGUAGES)[number]

export const PAID_OPTIONS = ['true', 'false'] as const
export type PaidOption = (typeof PAID_OPTIONS)[number]

/**
 * Everything the user can choose on the postings page. It lives in the URL, so a search can be
 * shared and the back button works; the parameter names are the ones the API uses.
 */
export interface SearchFilters {
  q: string
  domains: Domain[]
  types: OpportunityType[]
  /** Company slug. */
  company: string
  city: string
  remote: RemoteType | ''
  language: Language | ''
  paid: PaidOption | ''
  /** Also show postings that are not classified as early career (`earlyCareer=false`). */
  allLevels: boolean
  /** Also show postings outside Romania (`romaniaOnly=false`). */
  outsideRomania: boolean
  /** Zero-based page number. */
  page: number
}

/** A change to the filters; a change always goes back to the first page. */
export type FilterPatch = Partial<Omit<SearchFilters, 'page'>>

export const EMPTY_FILTERS: SearchFilters = {
  q: '',
  domains: [],
  types: [],
  company: '',
  city: '',
  remote: '',
  language: '',
  paid: '',
  allLevels: false,
  outsideRomania: false,
  page: 0,
}

/** Reads the filters from a query string. Unknown or repeated values are dropped, never an error. */
export function parseFilters(params: URLSearchParams): SearchFilters {
  return {
    q: params.get('q')?.trim() ?? '',
    domains: pick(params.getAll('domain'), DOMAINS),
    types: pick(params.getAll('type'), OPPORTUNITY_TYPES),
    company: params.get('company')?.trim() ?? '',
    city: params.get('city')?.trim() ?? '',
    remote: parseOption(params.get('remote'), REMOTE_TYPES) ?? '',
    language: parseOption(params.get('language'), LANGUAGES) ?? '',
    paid: parseOption(params.get('paid'), PAID_OPTIONS) ?? '',
    allLevels: params.get('earlyCareer') === 'false',
    outsideRomania: params.get('romaniaOnly') === 'false',
    page: parsePage(params.get('page')),
  }
}

/** The query string for the browser URL and for the API; defaults are left out. */
export function toSearchParams(filters: SearchFilters): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.q) params.set('q', filters.q)
  for (const domain of filters.domains) params.append('domain', domain)
  for (const type of filters.types) params.append('type', type)
  if (filters.company) params.set('company', filters.company)
  if (filters.city) params.set('city', filters.city)
  if (filters.remote) params.set('remote', filters.remote)
  if (filters.language) params.set('language', filters.language)
  if (filters.paid) params.set('paid', filters.paid)
  if (filters.allLevels) params.set('earlyCareer', 'false')
  if (filters.outsideRomania) params.set('romaniaOnly', 'false')
  if (filters.page > 0) params.set('page', String(filters.page))
  return params
}

export function changeFilters(current: SearchFilters, patch: FilterPatch): SearchFilters {
  return { ...current, ...patch, page: 0 }
}

/** Adds the value to the list, or removes it when it is already there. */
export function toggle<T>(values: readonly T[], value: T): T[] {
  return values.includes(value) ? values.filter((item) => item !== value) : [...values, value]
}

/** How many of the filter groups are in use; the page number does not count. */
export function activeFilterCount(filters: SearchFilters): number {
  return [
    filters.q !== '',
    filters.city !== '',
    filters.domains.length > 0,
    filters.types.length > 0,
    filters.company !== '',
    filters.remote !== '',
    filters.language !== '',
    filters.paid !== '',
    filters.allLevels,
    filters.outsideRomania,
  ].filter(Boolean).length
}

/** The value if it is one of the allowed ones, otherwise null. */
export function parseOption<T extends string>(value: string | null, allowed: readonly T[]): T | null {
  return allowed.find((candidate) => candidate === value) ?? null
}

/** The allowed values that appear in `values`, in the canonical order and without duplicates. */
function pick<T extends string>(values: string[], allowed: readonly T[]): T[] {
  return allowed.filter((candidate) => values.includes(candidate))
}

function parsePage(value: string | null): number {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : 0
}
