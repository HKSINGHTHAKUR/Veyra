import { cn } from '../../lib/cn'
import { addDays, toLocalDateValue } from './dates'

const HORIZON_DAYS = 60

/**
 * Date picker for availability lookup: quick-pick chips for the next two
 * weeks plus a bounded native date input. Past dates are not selectable.
 */
export default function DateSelector({ value, onChange }) {
  const today = new Date()
  const min = toLocalDateValue(today)
  const max = toLocalDateValue(addDays(today, HORIZON_DAYS))
  const chips = Array.from({ length: 14 }, (_, i) => addDays(today, i))

  return (
    <div>
      <div className="flex flex-wrap gap-2" role="group" aria-label="Choose a date">
        {chips.map((d, i) => {
          const v = toLocalDateValue(d)
          const selected = v === value
          return (
            <button
              key={v}
              type="button"
              aria-pressed={selected}
              onClick={() => onChange(v)}
              className={cn(
                'rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors',
                selected
                  ? 'border-primary-600 bg-primary-600 text-white'
                  : 'border-slate-200 bg-white text-slate-600 hover:border-primary-300 hover:text-primary-700'
              )}
            >
              {i === 0
                ? 'Today'
                : i === 1
                  ? 'Tomorrow'
                  : d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}
            </button>
          )
        })}
      </div>
      <label className="mt-3 block text-xs font-medium text-slate-600" htmlFor="booking-date">
        Or pick another date
      </label>
      <input
        id="booking-date"
        type="date"
        className="input-base mt-1 w-full sm:max-w-[12rem]"
        min={min}
        max={max}
        value={value}
        onChange={(e) => {
          if (e.target.value) onChange(e.target.value)
        }}
      />
    </div>
  )
}
