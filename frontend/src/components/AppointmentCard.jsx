import StatusBadge from './StatusBadge'
import { cn } from '../lib/cn'

function formatInstant(instant) {
  if (!instant) return '—'
  const d = new Date(instant)
  return d.toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Appointment summary card. Works for both patient view (shows doctor) and
 * doctor view (shows patient email). Responsive: card on small screens.
 */
export default function AppointmentCard({
  appointment,
  viewer = 'patient',
  onClick,
  className,
}) {
  const a = appointment
  const counterparty =
    viewer === 'patient'
      ? `Dr. ${a.doctorEmail ? a.doctorEmail.split('@')[0] : ''}`.trim() +
        (a.doctorSpecialisation ? ` · ${a.doctorSpecialisation}` : '')
      : a.patientEmail || 'Patient'

  return (
    <div
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onClick={onClick}
      onKeyDown={(e) => {
        if (onClick && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault()
          onClick()
        }
      }}
      className={cn(
        'card-base p-4',
        onClick && 'cursor-pointer transition-shadow hover:shadow-pop focus-visible:shadow-pop',
        className
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-slate-900">{counterparty}</p>
          <p className="mt-1 text-sm text-slate-500">{formatInstant(a.appointmentStart)}</p>
        </div>
        <StatusBadge status={a.status} />
      </div>
      {a.symptomText && (
        <p className="mt-3 line-clamp-2 border-t border-slate-100 pt-3 text-xs text-slate-500">
          <span className="font-medium text-slate-600">Symptoms: </span>
          {a.symptomText}
        </p>
      )}
    </div>
  )
}
