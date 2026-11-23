import { Link } from 'react-router'
import { useDocumentTitle } from '../lib/useDocumentTitle.ts'

interface NotFoundPageProps {
  title?: string
  message?: string
}

export default function NotFoundPage({
  title = 'Page not found',
  message = 'The page you are looking for does not exist.',
}: NotFoundPageProps) {
  useDocumentTitle(`${title} | Init`)

  return (
    <div className="mx-auto max-w-xl space-y-4 py-16 text-center">
      <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
      <p className="text-slate-600 dark:text-slate-300">{message}</p>
      <Link to="/" className="btn-primary">
        Back to postings
      </Link>
    </div>
  )
}
