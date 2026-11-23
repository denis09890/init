import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { formatDate, formatLocations, formatRelative, postedLabel } from './format.ts'

const NOW = new Date('2026-11-23T10:00:00Z')

describe('formatRelative', () => {
  it('says how many days ago', () => {
    assert.equal(formatRelative('2026-11-20T10:00:00Z', NOW), '3 days ago')
  })

  it('says today and yesterday in words', () => {
    assert.equal(formatRelative('2026-11-23T08:00:00Z', NOW), 'today')
    assert.equal(formatRelative('2026-11-22T10:00:00Z', NOW), 'yesterday')
  })

  it('switches to months and years for older dates', () => {
    assert.equal(formatRelative('2026-09-24T10:00:00Z', NOW), '2 months ago')
    assert.equal(formatRelative('2025-10-20T10:00:00Z', NOW), 'last year')
  })

  it('returns an empty string for an unreadable date', () => {
    assert.equal(formatRelative('not a date', NOW), '')
  })
})

describe('formatDate', () => {
  it('writes the day, month name and year', () => {
    assert.equal(formatDate('2026-11-20T10:00:00Z', 'UTC'), '20 Nov 2026')
  })

  it('returns an empty string for an unreadable date', () => {
    assert.equal(formatDate('nope'), '')
  })
})

describe('formatLocations', () => {
  it('joins several locations', () => {
    assert.equal(formatLocations(['Cluj-Napoca', 'Bucharest, RO']), 'Cluj-Napoca / Bucharest, RO')
  })

  it('says so when there is none', () => {
    assert.equal(formatLocations([]), 'Location not specified')
  })
})

describe('postedLabel', () => {
  it('prefers the date of the posting itself', () => {
    const label = postedLabel({ postedAt: '2026-11-20T10:00:00Z', firstSeenAt: '2026-11-22T10:00:00Z' })

    assert.match(label, /^Posted /)
  })

  it('falls back to when Init first saw it', () => {
    const label = postedLabel({ postedAt: null, firstSeenAt: '2026-11-22T10:00:00Z' })

    assert.match(label, /^Added /)
  })
})
