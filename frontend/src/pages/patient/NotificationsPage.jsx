import { useEffect, useState } from 'react'
import PageHeader from '../../components/PageHeader'
import Button from '../../components/Button'
import NotificationItem from '../../components/NotificationItem'
import NotificationDetail from '../../components/notifications/NotificationDetail'
import { Skeleton } from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import { Select } from '../../components/Input'
import { myNotifications } from '../../api/notifications'
import {
  STATUS_FILTER_OPTIONS,
  TYPE_FILTER_OPTIONS,
} from '../../components/notifications/labels'
import { useAuth } from '../../context/AuthContext'

/**
 * Shared inbox UI for patient and doctor portals (same endpoint — the backend
 * scopes results to the caller). The backend has no read/unread state, so the
 * inbox is a notification history/status view: no unread counters or
 * mark-as-read actions exist here on purpose.
 */
export default function NotificationsPage() {
  const { role } = useAuth()
  const [items, setItems] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [openId, setOpenId] = useState(null)

  async function load(nextStatus = status, nextType = type) {
    setLoading(true)
    setError(null)
    try {
      setItems(await myNotifications({ status: nextStatus, type: nextType }))
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load('', '')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function onFilter(key, value) {
    if (key === 'status') setStatus(value)
    else setType(value)
    load(key === 'status' ? value : status, key === 'type' ? value : type)
  }

  const hasFilters = Boolean(status || type)

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle="Everything the system has sent you, newest first."
        actions={
          <Button variant="secondary" size="sm" loading={loading} onClick={() => load()}>
            Refresh
          </Button>
        }
      />

      <div className="mb-4 flex max-w-md flex-col gap-3 sm:flex-row">
        <Select
          label="Status"
          value={status}
          onChange={(e) => onFilter('status', e.target.value)}
          placeholder="All statuses"
          options={STATUS_FILTER_OPTIONS}
        />
        <Select
          label="Type"
          value={type}
          onChange={(e) => onFilter('type', e.target.value)}
          placeholder="All types"
          options={TYPE_FILTER_OPTIONS}
        />
      </div>

      {loading && !items && (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-20" />)}
        </div>
      )}

      {!loading && error && <ErrorState error={error} onRetry={() => load()} />}

      {!loading && !error && items?.length === 0 && (
        <EmptyState
          icon="🔔"
          title="No notifications"
          description={
            hasFilters
              ? 'Nothing matches these filters. Try a different status or type.'
              : 'Nothing here yet. Booking confirmations and reminders will appear here.'
          }
        />
      )}

      {items?.length > 0 && (
        <ul className="space-y-3">
          {items.map((n) => <NotificationItem key={n.id} notification={n} onOpen={setOpenId} />)}
        </ul>
      )}

      <NotificationDetail id={openId} role={role} onClose={() => setOpenId(null)} />
    </>
  )
}
