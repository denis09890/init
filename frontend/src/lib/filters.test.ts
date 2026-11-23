import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  activeFilterCount,
  changeFilters,
  EMPTY_FILTERS,
  parseFilters,
  toggle,
  toSearchParams,
} from './filters.ts'

const FULL_QUERY =
  'q=java&domain=SOFTWARE&domain=DATA&type=INTERNSHIP&company=bosch&city=Cluj&remote=HYBRID' +
  '&language=ro&paid=true&earlyCareer=false&romaniaOnly=false&page=2'

describe('parseFilters', () => {
  it('gives the defaults for an empty query string', () => {
    assert.deepEqual(parseFilters(new URLSearchParams()), EMPTY_FILTERS)
  })

  it('reads every parameter', () => {
    assert.deepEqual(parseFilters(new URLSearchParams(FULL_QUERY)), {
      q: 'java',
      domains: ['SOFTWARE', 'DATA'],
      types: ['INTERNSHIP'],
      company: 'bosch',
      city: 'Cluj',
      remote: 'HYBRID',
      language: 'ro',
      paid: 'true',
      allLevels: true,
      outsideRomania: true,
      page: 2,
    })
  })

  it('drops unknown and repeated values instead of failing', () => {
    const filters = parseFilters(
      new URLSearchParams('domain=NOPE&domain=DATA&domain=DATA&remote=SIDEWAYS&language=fr&paid=maybe'),
    )

    assert.deepEqual(filters.domains, ['DATA'])
    assert.equal(filters.remote, '')
    assert.equal(filters.language, '')
    assert.equal(filters.paid, '')
  })

  it('lists the values in the canonical order whatever order the URL has', () => {
    const filters = parseFilters(new URLSearchParams('domain=DATA&domain=SOFTWARE&type=JUNIOR&type=INTERNSHIP'))

    assert.deepEqual(filters.domains, ['SOFTWARE', 'DATA'])
    assert.deepEqual(filters.types, ['INTERNSHIP', 'JUNIOR'])
  })

  it('trims the text fields', () => {
    const filters = parseFilters(new URLSearchParams({ q: '  practica ', city: ' Iasi  ' }))

    assert.equal(filters.q, 'practica')
    assert.equal(filters.city, 'Iasi')
  })

  it('only treats an explicit false as a wish to see everything', () => {
    const filters = parseFilters(new URLSearchParams('earlyCareer=true&romaniaOnly=yes'))

    assert.equal(filters.allLevels, false)
    assert.equal(filters.outsideRomania, false)
  })

  it('accepts only whole positive page numbers', () => {
    const page = (value: string) => parseFilters(new URLSearchParams({ page: value })).page

    assert.equal(page('3'), 3)
    assert.equal(page('0'), 0)
    assert.equal(page('-3'), 0)
    assert.equal(page('1.5'), 0)
    assert.equal(page('abc'), 0)
    assert.equal(page(''), 0)
  })
})

describe('toSearchParams', () => {
  it('leaves the defaults out', () => {
    assert.equal(toSearchParams(EMPTY_FILTERS).toString(), '')
  })

  it('round-trips a full set of filters', () => {
    const filters = parseFilters(new URLSearchParams(FULL_QUERY))

    assert.equal(toSearchParams(filters).toString(), FULL_QUERY)
  })

  it('encodes text safely', () => {
    const params = toSearchParams({ ...EMPTY_FILTERS, q: 'c++ & "stagiu"', city: 'Timișoara' })

    assert.equal(params.get('q'), 'c++ & "stagiu"')
    assert.equal(parseFilters(new URLSearchParams(params.toString())).city, 'Timișoara')
  })

  it('does not write the first page', () => {
    assert.equal(toSearchParams({ ...EMPTY_FILTERS, page: 0 }).has('page'), false)
    assert.equal(toSearchParams({ ...EMPTY_FILTERS, page: 1 }).get('page'), '1')
  })
})

describe('changeFilters', () => {
  it('goes back to the first page', () => {
    const changed = changeFilters({ ...EMPTY_FILTERS, page: 3 }, { q: 'java' })

    assert.equal(changed.q, 'java')
    assert.equal(changed.page, 0)
  })

  it('keeps the filters that were not changed', () => {
    const changed = changeFilters({ ...EMPTY_FILTERS, company: 'bosch', page: 2 }, { city: 'Cluj' })

    assert.equal(changed.company, 'bosch')
    assert.equal(changed.city, 'Cluj')
  })
})

describe('toggle', () => {
  it('adds a missing value and removes a present one', () => {
    assert.deepEqual(toggle(['a'], 'b'), ['a', 'b'])
    assert.deepEqual(toggle(['a', 'b'], 'a'), ['b'])
  })

  it('does not change the list it was given', () => {
    const values = ['a']
    toggle(values, 'b')

    assert.deepEqual(values, ['a'])
  })
})

describe('activeFilterCount', () => {
  it('is zero without filters, whatever the page', () => {
    assert.equal(activeFilterCount(EMPTY_FILTERS), 0)
    assert.equal(activeFilterCount({ ...EMPTY_FILTERS, page: 4 }), 0)
  })

  it('counts every group once', () => {
    assert.equal(activeFilterCount(parseFilters(new URLSearchParams(FULL_QUERY))), 10)
    assert.equal(activeFilterCount({ ...EMPTY_FILTERS, domains: ['SOFTWARE', 'DATA'] }), 1)
  })
})
