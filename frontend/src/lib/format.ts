import type { Posting } from '../api/types.ts'

const DAY_MS = 86_400_000

/** "3 days ago", "yesterday", "2 months ago"; an empty string for an unreadable date. */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const milliseconds = new Date(iso).getTime() - now.getTime()
  if (Number.isNaN(milliseconds)) {
    return ''
  }
  const days = Math.round(milliseconds / DAY_MS)
  const formatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
  if (Math.abs(days) < 30) {
    return formatter.format(days, 'day')
  }
  const months = Math.round(days / 30.44)
  if (Math.abs(months) < 12) {
    return formatter.format(months, 'month')
  }
  return formatter.format(Math.round(days / 365.25), 'year')
}

/** "20 Nov 2026". */
export function formatDate(iso: string, timeZone?: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeZone }).format(date)
}

export function formatLocations(locations: readonly string[]): string {
  return locations.length === 0 ? 'Location not specified' : locations.join(' / ')
}

/** When the posting appeared: its own date if the source gave one, else when Init first saw it. */
export function postedLabel(posting: Pick<Posting, 'postedAt' | 'firstSeenAt'>): string {
  return posting.postedAt
    ? `Posted ${formatRelative(posting.postedAt)}`
    : `Added ${formatRelative(posting.firstSeenAt)}`
}
