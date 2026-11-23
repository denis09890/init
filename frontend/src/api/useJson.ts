import { useEffect, useState } from 'react'
import { getJson } from './client.ts'

interface Settled<T> {
  /** Which request this outcome belongs to. */
  key: string
  data: T | null
  error: Error | null
}

export interface JsonResource<T> {
  /**
   * The latest data that arrived. While a new request is loading this is still the previous
   * result, so a page of postings does not flash empty when a filter changes.
   */
  data: T | null
  /** The error of the current request, if it failed. */
  error: Error | null
  /** Whether the current request has not finished yet. */
  loading: boolean
  retry: () => void
}

/**
 * Loads JSON from `url` whenever it changes. A request that is no longer wanted is
 * aborted, and an outcome is only used for the request it belongs to.
 */
export function useJson<T>(url: string): JsonResource<T> {
  const [attempt, setAttempt] = useState(0)
  const [settled, setSettled] = useState<Settled<T> | null>(null)
  const key = `${attempt}|${url}`

  useEffect(() => {
    const controller = new AbortController()
    getJson<T>(url, controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) {
          setSettled({ key, data, error: null })
        }
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setSettled((previous) => ({ key, data: previous?.data ?? null, error: toError(error) }))
        }
      },
    )
    return () => controller.abort()
  }, [url, key])

  const current = settled !== null && settled.key === key
  return {
    data: settled?.data ?? null,
    error: current ? settled.error : null,
    loading: !current,
    retry: () => setAttempt((value) => value + 1),
  }
}

function toError(error: unknown): Error {
  return error instanceof Error ? error : new Error(String(error))
}
