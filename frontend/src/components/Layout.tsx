import { Link, Outlet } from 'react-router'

export default function Layout() {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="flex items-baseline gap-3">
            <span className="text-2xl font-bold tracking-tight text-indigo-600 dark:text-indigo-400">
              Init
            </span>
            <span className="hidden text-sm text-slate-500 sm:inline dark:text-slate-400">
              Init your career.
            </span>
          </Link>
          <a
            href="https://github.com/denis09890/init"
            target="_blank"
            rel="noopener noreferrer"
            className="text-sm text-slate-600 hover:text-indigo-600 dark:text-slate-400 dark:hover:text-indigo-400"
          >
            Source code
          </a>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 dark:border-slate-800">
        <p className="mx-auto max-w-6xl px-4 py-4 text-xs text-slate-500 dark:text-slate-400">
          Postings are collected from companies' public career pages and job-board APIs. Init shows a short
          preview and always links to the original posting.
        </p>
      </footer>
    </div>
  )
}
