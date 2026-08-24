import { Link } from 'react-router-dom'

/**
 * Restrained quick-action tiles for dashboards. Every action must be a real
 * existing route — dashboards summarize the product, they don't add features.
 */
export default function QuickActions({ actions }) {
  return (
    <div className="flex flex-wrap gap-3">
      {actions.map((a) => (
        <Link
          key={a.to}
          to={a.to}
          className="card-base group min-w-[11rem] flex-1 px-4 py-3 transition-shadow hover:shadow-pop"
        >
          <p className="text-sm font-semibold text-slate-900">{a.label}</p>
          <p className="mt-0.5 text-xs text-slate-500">{a.description}</p>
        </Link>
      ))}
    </div>
  )
}
