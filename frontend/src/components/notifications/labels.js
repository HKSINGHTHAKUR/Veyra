/**
 * Human-readable labels for the backend NotificationType / NotificationStatus
 * enums. Values must stay in sync with the Java enums — never add types here
 * that the backend does not define.
 */

export const TYPE_LABELS = {
  BOOKING_CONFIRMATION: 'Booking confirmation',
  APPOINTMENT_REMINDER: 'Appointment reminder',
  CANCELLATION: 'Appointment cancelled',
  RESCHEDULE: 'Appointment rescheduled',
  DOCTOR_LEAVE: 'Doctor unavailable',
  MEDICATION_REMINDER: 'Medication reminder',
}

export const STATUS_LABELS = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  SENT: 'Sent',
  FAILED: 'Failed',
}

export function typeLabel(type) {
  return TYPE_LABELS[type] || String(type || '').replace(/_/g, ' ')
}

export function statusLabel(status) {
  return STATUS_LABELS[status] || String(status || '').replace(/_/g, ' ')
}

/**
 * User-facing status for the notification UI. Internal email-delivery details
 * are not shown to end users: FAILED delivery renders no badge (the underlying
 * event, e.g. the booking, is still valid), and SENT is shown as the neutral
 * event-confirmed presentation. PENDING/PROCESSING stay visible as in-progress.
 * The backend status is never changed — this is presentation only.
 */
export function userStatus(status) {
  if (status === 'FAILED') return null
  if (status === 'SENT') return 'CONFIRMED'
  return status
}

export const TYPE_FILTER_OPTIONS = Object.entries(TYPE_LABELS).map(([value, label]) => ({
  value,
  label,
}))

export const STATUS_FILTER_OPTIONS = Object.entries(STATUS_LABELS).map(([value, label]) => ({
  value,
  label,
}))
