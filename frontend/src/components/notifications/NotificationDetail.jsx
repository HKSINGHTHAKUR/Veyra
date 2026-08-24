import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Modal from '../Modal'
import Button from '../Button'
import StatusBadge from '../StatusBadge'
import { Skeleton } from '../LoadingSpinner'
import { getNotification } from '../../api/notifications'
import { toApiError } from '../../api/client'
import { typeLabel } from './labels'

function formatWhen(instant) {
  if (!instant) return ''
  return new Date(instant).toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function appointmentPath(role, appointmentId) {
  return role === 'DOCTOR'
    ? `/doctor/appointments/${appointmentId}`
    : `/patient/appointments/${appointmentId}`
}

/**
 * Notification detail modal. Fetches the single notification through the
 * existing API (backend enforces ownership), shows subject/body/status/
 * timestamp and links to the related appointment when one exists.
 * No read/unread state exists in the backend, so none is shown.
 */
export default function NotificationDetail({ id, role, onClose }) {
  const navigate = useNavigate()
  const [notification, setNotification] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function load(notificationId) {
    setLoading(true)
    setError(null)
    setNotification(null)
    try {
      setNotification(await getNotification(notificationId))
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (id) load(id)
  }, [id])

  return (
    <Modal
      open={Boolean(id)}
      onClose={onClose}
      title={notification?.subject || 'Notification'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
          {notification?.appointmentId && (
            <Button
              onClick={() => {
                onClose()
                navigate(appointmentPath(role, notification.appointmentId))
              }}
            >
              View appointment
            </Button>
          )}
        </>
      }
    >
      {loading && <Skeleton className="h-32" />}

      {!loading && error && (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {toApiError(error).status === 404
            ? 'This notification no longer exists.'
            : 'Unable to load this notification. Please try again.'}
          <button
            type="button"
            onClick={() => load(id)}
            className="ml-2 font-medium underline underline-offset-2"
          >
            Retry
          </button>
        </div>
      )}

      {!loading && !error && notification && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={notification.status} />
            <span className="text-xs text-slate-500">{typeLabel(notification.type)}</span>
          </div>

          {notification.status === 'FAILED' && (
            <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs leading-relaxed text-amber-800">
              Delivery failed — the email could not be sent. This does not change
              your appointment itself.
            </p>
          )}

          {notification.body && (
            <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">
              {notification.body}
            </p>
          )}

          <p className="text-xs text-slate-400">
            {notification.createdAt ? `Received ${formatWhen(notification.createdAt)}` : ''}
            {notification.status === 'SENT' && notification.sentAt && (
              <> · Delivered {formatWhen(notification.sentAt)}</>
            )}
          </p>
        </div>
      )}
    </Modal>
  )
}
