import type { Domain, OpportunityType, RemoteType } from '../api/types.ts'
import type { Language } from './filters.ts'

export const DOMAIN_LABELS: Record<Domain, string> = {
  SOFTWARE: 'Software',
  DATA: 'Data & AI',
  ENGINEERING: 'Engineering',
  DESIGN: 'Design',
  PRODUCT: 'Product',
  FINANCE: 'Finance & accounting',
  MARKETING: 'Marketing',
  SALES: 'Sales',
  HR: 'HR',
  LEGAL: 'Legal',
  OPERATIONS: 'Operations & logistics',
  CUSTOMER_SUPPORT: 'Customer support',
  RESEARCH: 'Research',
  OTHER: 'Other',
}

export const OPPORTUNITY_TYPE_LABELS: Record<OpportunityType, string> = {
  INTERNSHIP: 'Internship',
  WORKING_STUDENT: 'Working student',
  GRADUATE_PROGRAM: 'Graduate / trainee program',
  JUNIOR: 'Junior / entry level',
  OTHER: 'Other',
}

export const REMOTE_LABELS: Record<RemoteType, string> = {
  ONSITE: 'On-site',
  HYBRID: 'Hybrid',
  REMOTE: 'Remote',
  UNKNOWN: 'Not specified',
}

export const LANGUAGE_LABELS: Record<Language, string> = {
  en: 'English',
  ro: 'Romanian',
}
