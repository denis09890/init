/**
 * The link to the original posting, but only when it is a plain web address. The value comes from
 * another company's data, so anything else (`javascript:`, `data:`, ...) must never end up in an
 * `href`.
 */
export function safeExternalUrl(value: string): string | null {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null
  } catch {
    return null
  }
}
