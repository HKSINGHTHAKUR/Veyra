import { cn } from '../lib/cn'

/**
 * Status → visual language. Color is reserved for status, never decoration.
 * Keys are matched case-insensitively against backend enum values.
 */
const MAP = {
  BOOKED: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  CONFIRMED: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  RESCHEDULED: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  PENDING: 'bg-amber-50 text-amber-700 ring-amber-600/25',
  PROCESSING: 'bg-amber-50 text-amber-700 ring-amber-600/25',
  SENT: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  SYNCED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  COMPLETED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  ACTIVE: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  CONVERTED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  CANCELLED: 'bg-slate-100 text-slate-600 ring-slate-500/20',
  RELEASED: 'bg-slate-100 text-slate-600 ring-slate-500/20',
  EXPIRED: 'bg-slate-100 text-slate-600 ring-slate-500/20',
  NO_SHOW: 'bg-slate-100 text-slate-600 ring-slate-500/20',
  FAILED: 'bg-red-50 text-red-700 ring-red-600/20',
  HIGH: 'bg-red-50 text-red-700 ring-red-600/20',
  URGENT: 'bg-red-50 text-red-700 ring-red-600/20',
  MEDIUM: 'bg-amber-50 text-amber-700 ring-amber-600/25',
  LOW: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
}

export default function StatusBadge({ status, className }) {
  if (!status) return null
  const key = String(status).toUpperCase().replace(/[\s-]+/g, '_')
  const cls = MAP[key] || 'bg-slate-100 text-slate-600 ring-slate-500/20'
  const label = String(status).replace(/_/g, ' ')
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2 py-0.5 text-2xs font-semibold uppercase tracking-wide ring-1 ring-inset',
        cls,
        className
      )}
    >
      {label}
    </span>
  )
}
