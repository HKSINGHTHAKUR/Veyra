import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-surface p-6">
      <div className="card-base max-w-md p-8 text-center">
        <p className="text-2xs font-semibold uppercase tracking-widest text-slate-400">404</p>
        <h1 className="mt-1 text-base font-semibold text-slate-900">Page not found</h1>
        <p className="mt-2 text-sm text-slate-500">
          The page you are looking for does not exist or has moved.
        </p>
        <Link
          to="/"
          className="mt-5 inline-flex h-9 items-center rounded-lg bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700"
        >
          Back to start
        </Link>
      </div>
    </div>
  )
}
