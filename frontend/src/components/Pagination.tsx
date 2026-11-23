interface PaginationProps {
  /** Zero-based current page. */
  page: number
  totalPages: number
  onPage: (page: number) => void
}

export default function Pagination({ page, totalPages, onPage }: PaginationProps) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <nav aria-label="Pagination" className="mt-6 flex items-center justify-between gap-4">
      <button type="button" className="btn-secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        ← Previous
      </button>
      <p className="text-sm text-slate-600 dark:text-slate-400">
        Page {page + 1} of {totalPages}
      </p>
      <button
        type="button"
        className="btn-secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
      >
        Next →
      </button>
    </nav>
  )
}
