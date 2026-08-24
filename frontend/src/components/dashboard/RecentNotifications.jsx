import { Link } from 'react-router-dom'
import StatusBadge from '../StatusBadge'
import { typeLabel } from '../notifications/labels'

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
 * Compact notification preview for dashboards. Rows link nowhere themselves —
 * the full inbox owns the detail experience; this is a glanceable summary.
 */
export default function RecentNotifications({ notifications, inboxPath }) {
  return (
    <section className="card-base p-5" aria-label="Recent notifications">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-900">Recent notifications</h2>
        <Link
          to={inboxPath}
          className="text-xs font-semibold text-primary-700 hover:text-primary-800"
        >
          Open inbox
        </Link>
      </div>

      {notifications.length === 0 ? (
        <p className="text-sm text-slate-500">No recent notifications.</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {notifications.map((n) => (
            <li key={n.id} className="py-2.5 first:pt-0 last:pb-0">
              <div className="flex items-center justify-between gap-2">
                <p className="min-w-0 truncate text-sm font-medium text-slate-800">
                  {n.subject || typeLabel(n.type)}
                </p>
                <StatusBadge status={n.status} />
              </div>
              <p className="mt-0.5 text-xs text-slate-400">
                {typeLabel(n.type)}
                {n.createdAt && <> · {formatWhen(n.createdAt)}</>}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
