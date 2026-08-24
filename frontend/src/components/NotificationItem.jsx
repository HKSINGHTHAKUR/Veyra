import StatusBadge from './StatusBadge'
import { typeLabel } from './notifications/labels'

function formatWhen(instant) {
  if (!instant) return ''
  return new Date(instant).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Single notification row for the inbox. The whole row is a real button that
 * opens the notification detail. Internal delivery fields (attempt counts,
 * failure reasons) are intentionally not shown — this is a user inbox, not an
 * operations log.
 */
export default function NotificationItem({ notification, onOpen }) {
  const n = notification
  return (
    <li className="card-base">
      <button
        type="button"
        onClick={() => onOpen?.(n.id)}
        aria-label={`Open notification: ${n.subject || typeLabel(n.type)}`}
        className="flex w-full items-start gap-3 rounded-card p-4 text-left transition-colors hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
      >
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <p className="text-sm font-semibold text-slate-900">{n.subject || typeLabel(n.type)}</p>
            <StatusBadge status={n.status} />
          </div>
          {n.body && (
            <p className="mt-1 line-clamp-2 whitespace-pre-line text-sm text-slate-600">{n.body}</p>
          )}
          {n.status === 'FAILED' && (
            <p className="mt-1 text-xs text-amber-700">
              Delivery failed — your appointment is not affected.
            </p>
          )}
          <p className="mt-2 text-2xs uppercase tracking-wide text-slate-400">
            {typeLabel(n.type)}
            {n.createdAt && <> · {formatWhen(n.createdAt)}</>}
          </p>
        </div>
      </button>
    </li>
  )
}
