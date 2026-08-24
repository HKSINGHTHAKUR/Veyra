import Button from '../Button'

/**
 * Inline section-level failure: one API failing must not blank the whole
 * dashboard — the broken section reports itself and offers a retry.
 */
export default function SectionError({ message, onRetry }) {
  return (
    <div
      role="alert"
      className="card-base flex flex-wrap items-center justify-between gap-3 px-4 py-3"
    >
      <p className="text-sm font-medium text-red-700">{message}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  )
}
