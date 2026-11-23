import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { safeExternalUrl } from './url.ts'

describe('safeExternalUrl', () => {
  it('accepts https and http addresses', () => {
    assert.equal(
      safeExternalUrl('https://job-boards.greenhouse.io/cresta/jobs/4123841008'),
      'https://job-boards.greenhouse.io/cresta/jobs/4123841008',
    )
    assert.equal(safeExternalUrl('http://example.com/jobs'), 'http://example.com/jobs')
  })

  it('rejects every other kind of link', () => {
    assert.equal(safeExternalUrl('javascript:alert(1)'), null)
    assert.equal(safeExternalUrl('data:text/html,<script>alert(1)</script>'), null)
    assert.equal(safeExternalUrl('mailto:jobs@example.com'), null)
    assert.equal(safeExternalUrl('ftp://example.com/jobs'), null)
  })

  it('rejects text that is not an address', () => {
    assert.equal(safeExternalUrl('not a url'), null)
    assert.equal(safeExternalUrl(''), null)
    assert.equal(safeExternalUrl('/relative/path'), null)
  })
})
