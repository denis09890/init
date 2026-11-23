// Mirrors the JSON of the backend API (see docs/ARCHITECTURE.md, "REST API").
// The value lists are `as const` arrays rather than enums: they double as the allowed values when
// parsing the URL, and the TypeScript config forbids enums.

export const DOMAINS = [
  'SOFTWARE',
  'DATA',
  'ENGINEERING',
  'DESIGN',
  'PRODUCT',
  'FINANCE',
  'MARKETING',
  'SALES',
  'HR',
  'LEGAL',
  'OPERATIONS',
  'CUSTOMER_SUPPORT',
  'RESEARCH',
  'OTHER',
] as const
export type Domain = (typeof DOMAINS)[number]

export const OPPORTUNITY_TYPES = [
  'INTERNSHIP',
  'WORKING_STUDENT',
  'GRADUATE_PROGRAM',
  'JUNIOR',
  'OTHER',
] as const
export type OpportunityType = (typeof OPPORTUNITY_TYPES)[number]

export const REMOTE_TYPES = ['ONSITE', 'HYBRID', 'REMOTE', 'UNKNOWN'] as const
export type RemoteType = (typeof REMOTE_TYPES)[number]

export type Seniority = 'INTERN' | 'ENTRY' | 'JUNIOR' | 'MID' | 'SENIOR' | 'LEAD' | 'UNKNOWN'

export type PostingStatus = 'OPEN' | 'CLOSED'

export interface CompanyRef {
  name: string
  slug: string
}

/** A posting: a short snippet and a link to the original, never the full description. */
export interface Posting {
  id: number
  title: string
  company: CompanyRef
  url: string
  locations: string[]
  remoteType: RemoteType
  snippet: string | null
  postedAt: string | null
  firstSeenAt: string
  lastSeenAt: string
  closedAt: string | null
  status: PostingStatus
  earlyCareer: boolean
  locationEligible: boolean
  opportunityType: OpportunityType
  domain: Domain
  seniority: Seniority
  language: string | null
  paid: boolean | null
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface Company {
  name: string
  slug: string
  website: string | null
  openPostings: number
  earlyCareerPostings: number
}
