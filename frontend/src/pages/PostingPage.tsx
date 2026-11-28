import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { postingUrl } from '../api/endpoints.ts'
import type { Posting } from '../api/types.ts'
import { useJson } from '../api/useJson.ts'
import PostingBadges from '../components/PostingBadges.tsx'
import { ErrorPanel, LoadingDetail } from '../components/StatusViews.tsx'
import { formatDate, formatLocations } from '../lib/format.ts'
import { REMOTE_LABELS } from '../lib/labels.ts'
import { safeExternalUrl } from '../lib/url.ts'
import { DEFAULT_TITLE, useDocumentTitle } from '../lib/useDocumentTitle.ts'
import NotFoundPage from './NotFoundPage.tsx'

export default function PostingPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const from = backTarget(location.state)
  const resource = useJson<Posting>(postingUrl(id))
  // while another posting is loading, data may still be the previous one
  const posting = resource.data !== null && String(resource.data.id) === id ? resource.data : null

  useDocumentTitle(posting ? `${posting.title} · ${posting.company.name} | Init` : DEFAULT_TITLE)
  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [id])

  if (resource.error) {
    const error = resource.error
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) {
      return <NotFoundPage title="Posting not found" message="This posting does not exist, or it was removed." />
    }
    return <ErrorPanel error={error} onRetry={resource.retry} />
  }
  if (!posting) {
    return <LoadingDetail />
  }

  const original = safeExternalUrl(posting.url)
  return (
    <article className="mx-auto max-w-3xl">
      <Link to={from} className="text-sm text-indigo-600 hover:underline dark:text-indigo-400">
        ← Back to postings
      </Link>

      {posting.status === 'CLOSED' && (
        <p
          role="status"
          className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200"
        >
          This posting is no longer listed by the company
          {posting.closedAt ? ` (closed ${formatDate(posting.closedAt)})` : ''}.
        </p>
      )}

      <h1 className="mt-4 text-3xl font-bold tracking-tight">{posting.title}</h1>
      <p className="mt-2 text-slate-600 dark:text-slate-300">
        <Link
          to={`/?company=${encodeURIComponent(posting.company.slug)}`}
          className="font-medium hover:text-indigo-600 hover:underline dark:hover:text-indigo-400"
        >
          {posting.company.name}
        </Link>
      </p>

      <div className="mt-4">
        <PostingBadges posting={posting} />
      </div>

      <dl className="card mt-6 grid gap-4 p-4 sm:grid-cols-2">
        <Detail label="Location">{formatLocations(posting.locations)}</Detail>
        <Detail label="Workplace">{REMOTE_LABELS[posting.remoteType]}</Detail>
        <Detail label="Posted">{posting.postedAt ? formatDate(posting.postedAt) : 'Not stated'}</Detail>
        <Detail label="First seen by Init">{formatDate(posting.firstSeenAt)}</Detail>
      </dl>

      {posting.skills.length > 0 && (
        <section className="mt-6" aria-labelledby="skills-heading">
          <h2 id="skills-heading" className="text-sm font-medium text-slate-500 dark:text-slate-400">
            Skills mentioned
          </h2>
          <ul className="mt-2 flex flex-wrap gap-2">
            {posting.skills.map((skill) => (
              <li key={skill} className="chip">
                {skill}
              </li>
            ))}
          </ul>
        </section>
      )}

      {posting.snippet && (
        <section className="mt-6">
          <h2 className="text-sm font-medium text-slate-500 dark:text-slate-400">Preview</h2>
          <p className="mt-2 text-slate-700 dark:text-slate-200">{posting.snippet}</p>
        </section>
      )}

      <div className="mt-8">
        {original ? (
          <a href={original} target="_blank" rel="noopener noreferrer" className="btn-primary">
            View the full posting and apply ↗
          </a>
        ) : (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            The link to the original posting is not available.
          </p>
        )}
        <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
          You continue on the company's own site. Init does not host full descriptions or applications.
        </p>
      </div>
    </article>
  )
}

interface DetailProps {
  label: string
  children: ReactNode
}

function Detail({ label, children }: DetailProps) {
  return (
    <div>
      <dt className="text-sm text-slate-500 dark:text-slate-400">{label}</dt>
      <dd className="mt-0.5 font-medium">{children}</dd>
    </div>
  )
}

/** Where "back" leads: the list with the filters the user came from, or the plain list. */
function backTarget(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state) {
    const from = state.from
    if (typeof from === 'string' && from.startsWith('/')) {
      return from
    }
  }
  return '/'
}
