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

export const TYPE_FILTER_OPTIONS = Object.entries(TYPE_LABELS).map(([value, label]) => ({
  value,
  label,
}))

export const STATUS_FILTER_OPTIONS = Object.entries(STATUS_LABELS).map(([value, label]) => ({
  value,
  label,
}))
