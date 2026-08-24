import { cn } from '../../lib/cn'
import { formatSlotTime } from './dates'

/**
 * Slot grid for one day. Occupied slots stay visible (disabled, muted) so
 * patients understand why the schedule is limited. Times are shown in the
 * user's local timezone; selection passes the original UTC ISO string.
 */
export default function SlotGrid({ slots, selectedSlotStart, onSelect, disabled }) {
  return (
    <div className="grid grid-cols-3 gap-2 sm:grid-cols-4 lg:grid-cols-6" role="group" aria-label="Available time slots">
      {slots.map((slot) => {
        const selected = slot.slotStart === selectedSlotStart
        const label = formatSlotTime(slot.slotStart)
        return (
          <button
            key={slot.slotStart}
            type="button"
            disabled={!slot.available || disabled}
            aria-pressed={selected}
            aria-label={slot.available ? `Select ${label}` : `${label} — unavailable`}
            onClick={() => onSelect(slot)}
            className={cn(
              'rounded-lg border px-2 py-2 text-sm font-medium transition-colors',
              !slot.available &&
                'cursor-not-allowed border-slate-100 bg-slate-50 text-slate-300 line-through',
              slot.available &&
                !selected &&
                'border-slate-200 bg-white text-slate-700 hover:border-primary-400 hover:text-primary-700',
              selected && 'border-primary-600 bg-primary-600 text-white'
            )}
          >
            {label}
          </button>
        )
      })}
    </div>
  )
}
