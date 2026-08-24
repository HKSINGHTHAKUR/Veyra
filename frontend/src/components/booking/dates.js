/**
 * Date helpers for the booking flow.
 * Backend availability is queried with a local YYYY-MM-DD date, and slot
 * timestamps arrive as UTC Instants that are converted to local time for
 * display only — the original ISO string is always sent back unchanged.
 */

/** Local calendar date as YYYY-MM-DD (the format the availability API expects). */
export function toLocalDateValue(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function addDays(date, days) {
  const d = new Date(date)
  d.setDate(d.getDate() + days)
  return d
}

export function formatSlotTime(isoInstant) {
  return new Date(isoInstant).toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  })
}

export function formatSlotDate(isoInstant) {
  return new Date(isoInstant).toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  })
}

export function formatSlotDateTime(isoInstant) {
  return new Date(isoInstant).toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

/** mm:ss remaining until `expiresAt` (floored at 00:00). */
export function formatRemaining(ms) {
  const total = Math.max(0, Math.floor(ms / 1000))
  const m = String(Math.floor(total / 60)).padStart(2, '0')
  const s = String(total % 60).padStart(2, '0')
  return `${m}:${s}`
}
