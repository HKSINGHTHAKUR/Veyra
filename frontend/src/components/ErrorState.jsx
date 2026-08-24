import { toApiError } from '../api/client'
import Button from './Button'

/**
 * Consistent error surface for API-backed pages.
 * 403 renders a distinct unauthorized message (never auto-redirects).
 */
export default function ErrorState({ error, onRetry, title = 'Something went wrong' }) {
  const { status, message } = toApiError(error)
  const unauthorized = status === 403

  return (
    <div className="card-base flex flex-col items-center justify-center px-6 py-12 text-center">
      <div
        aria-hidden="true"
        className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-xl"
      >
        {unauthorized ? '🔒' : '⚠️'}
      </div>
      <h3 className="text-sm font-semibold text-slate-900">
        {unauthorized ? 'Access denied' : title}
      </h3>
      <p className="mt-1 max-w-sm text-sm text-slate-500">
        {message || 'An unexpected error occurred. Please try again.'}
      </p>
      {onRetry && !unauthorized && (
        <Button variant="secondary" size="sm" className="mt-5" onClick={onRetry}>
          Try again
        </Button>
      )}
      {!onRetry && !unauthorized && null}
    </div>
  )
}
