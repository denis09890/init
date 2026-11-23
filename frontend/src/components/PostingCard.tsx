import { Link } from 'react-router'
import type { Posting } from '../api/types.ts'
import { formatLocations, postedLabel } from '../lib/format.ts'
import PostingBadges from './PostingBadges.tsx'

interface PostingCardProps {
  posting: Posting
  /** The page to go back to from the detail view, so the filters are not lost. */
  from: string
}

export default function PostingCard({ posting, from }: PostingCardProps) {
  return (
    <article className="card p-4 transition-colors hover:border-indigo-300 dark:hover:border-indigo-700">
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1">
        <h2 className="text-lg leading-snug font-semibold">
          <Link
            to={`/postings/${posting.id}`}
            state={{ from }}
            className="hover:text-indigo-600 hover:underline dark:hover:text-indigo-400"
          >
            {posting.title}
          </Link>
        </h2>
        <p className="text-sm text-slate-500 dark:text-slate-400">{postedLabel(posting)}</p>
      </div>

      <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
        {posting.company.name} · {formatLocations(posting.locations)}
      </p>

      <div className="mt-3">
        <PostingBadges posting={posting} />
      </div>

      {posting.snippet && (
        <p className="mt-3 line-clamp-3 text-sm text-slate-600 dark:text-slate-300">{posting.snippet}</p>
      )}
    </article>
  )
}
