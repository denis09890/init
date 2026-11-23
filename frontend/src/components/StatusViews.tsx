import { ApiError } from '../api/client.ts'

/** Placeholder cards shown while the first page of postings loads. */
export function LoadingList() {
  return (
    <ul className="space-y-4" aria-hidden="true">
      {[0, 1, 2, 3].map((index) => (
        <li key={index} className="card animate-pulse space-y-3 p-4">
          <div className="h-5 w-2/3 rounded bg-slate-200 dark:bg-slate-700" />
          <div className="h-4 w-1/3 rounded bg-slate-200 dark:bg-slate-700" />
          <div className="h-4 w-full rounded bg-slate-200 dark:bg-slate-700" />
        </li>
      ))}
    </ul>
  )
}

/** Placeholder shown while a single posting loads. */
export function LoadingDetail() {
  return (
    <div className="mx-auto max-w-3xl animate-pulse space-y-4" aria-hidden="true">
      <div className="h-8 w-3/4 rounded bg-slate-200 dark:bg-slate-700" />
      <div className="h-4 w-1/3 rounded bg-slate-200 dark:bg-slate-700" />
      <div className="h-32 w-full rounded bg-slate-200 dark:bg-slate-700" />
    </div>
  )
}

interface ErrorPanelProps {
  error: Error
  onRetry: () => void
}

export function ErrorPanel({ error, onRetry }: ErrorPanelProps) {
  return (
    <div role="alert" className="card space-y-3 border-red-200 p-6 dark:border-red-900">
      <h2 className="text-lg font-semibold text-red-700 dark:text-red-400">Something went wrong</h2>
      <p className="text-sm text-slate-600 dark:text-slate-300">{explain(error)}</p>
      <button type="button" className="btn-secondary" onClick={onRetry}>
        Try again
      </button>
    </div>
  )
}

interface EmptyStateProps {
  /** Whether filters are active, which changes what the user can do about it. */
  filtered: boolean
  onClear: () => void
}

export function EmptyState({ filtered, onClear }: EmptyStateProps) {
  return (
    <div className="card space-y-3 p-8 text-center">
      <h2 className="text-lg font-semibold">
        {filtered ? 'No postings match these filters' : 'No postings yet'}
      </h2>
      <p className="text-sm text-slate-600 dark:text-slate-300">
        {filtered
          ? 'Try removing a filter or searching for something broader.'
          : 'The first ingestion may still be running. Check back in a minute.'}
      </p>
      {filtered && (
        <button type="button" className="btn-primary" onClick={onClear}>
          Clear all filters
        </button>
      )}
    </div>
  )
}

function explain(error: Error): string {
  if (error instanceof ApiError) {
    if (error.status === 0) {
      return 'The server could not be reached. Is the backend running?'
    }
    if (error.status === 400) {
      return error.detail ?? 'The request was not valid.'
    }
    return 'The server had a problem. Please try again in a moment.'
  }
  return 'An unexpected error occurred.'
}
