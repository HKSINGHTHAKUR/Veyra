import { cn } from '../lib/cn'

function initials(first = '', last = '') {
  return `${first?.[0] ?? ''}${last?.[0] ?? ''}`.toUpperCase() || 'Dr'
}

/** Doctor summary card for patient-facing search results. */
export default function DoctorCard({ doctor, onClick, className }) {
  const d = doctor
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick?.()
        }
      }}
      className={cn(
        'card-base flex items-start gap-4 p-4',
        'cursor-pointer transition-shadow hover:shadow-pop focus-visible:shadow-pop',
        className
      )}
    >
      <div
        aria-hidden="true"
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary-50 text-sm font-semibold text-primary-700"
      >
        {initials(d.firstName, d.lastName)}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-slate-900">
          Dr. {d.firstName} {d.lastName}
        </p>
        <p className="mt-0.5 text-xs font-medium uppercase tracking-wide text-primary-700">
          {d.specialisation}
        </p>
        {d.bio && <p className="mt-2 line-clamp-2 text-sm text-slate-500">{d.bio}</p>}
        {typeof d.consultationFee === 'number' && d.consultationFee > 0 && (
          <p className="mt-2 text-sm font-medium text-slate-700">
            ${Number(d.consultationFee).toFixed(2)}
            <span className="ml-1 text-xs font-normal text-slate-400">/ consultation</span>
          </p>
        )}
      </div>
    </div>
  )
}
