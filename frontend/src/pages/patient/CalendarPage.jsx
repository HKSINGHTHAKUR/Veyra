import { useEffect, useState } from 'react'
import PageHeader from '../../components/PageHeader'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import ErrorState from '../../components/ErrorState'
import { ConfirmDialog } from '../../components/Modal'
import { calendarStatus, connectCalendar, disconnectCalendar } from '../../api/calendar'
import { toApiError } from '../../api/client'

/**
 * Google Calendar integration card. The backend owns the whole OAuth flow:
 * connect returns a consent URL (single-use server-side state inside), the
 * browser is redirected to Google, and the backend callback stores the
 * credentials. The frontend never sees or stores any token.
 */
export default function CalendarPage() {
  const [status, setStatus] = useState(null)
  const [pageError, setPageError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [connecting, setConnecting] = useState(false)
  const [connectError, setConnectError] = useState(null)
  const [disconnecting, setDisconnecting] = useState(false)
  const [disconnectError, setDisconnectError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [confirmOpen, setConfirmOpen] = useState(false)

  async function load() {
    setLoading(true)
    setPageError(null)
    try {
      setStatus(await calendarStatus())
    } catch (e) {
      setPageError(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  async function onConnect() {
    setConnecting(true)
    setConnectError(null)
    setNotice(null)
    try {
      // Backend mints a single-use state bound to this user and returns the consent URL.
      const { authorizationUrl } = await connectCalendar()
      // Browser navigation on purpose — OAuth must happen outside the SPA.
      window.location.href = authorizationUrl
    } catch (e) {
      const { status: code } = toApiError(e)
      setConnectError(
        code === 403
          ? "You don't have permission to connect Google Calendar."
          : 'Unable to connect Google Calendar right now.'
      )
      setConnecting(false)
    }
  }

  async function onDisconnect() {
    setDisconnecting(true)
    setDisconnectError(null)
    try {
      await disconnectCalendar()
      await load()
      setNotice('Google Calendar disconnected.')
      setConfirmOpen(false)
    } catch (e) {
      setDisconnectError('Unable to disconnect Google Calendar. Please try again.')
    } finally {
      setDisconnecting(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Google Calendar"
        subtitle="Automatically keep your Veyra appointments synchronized with Google Calendar."
      />

      {loading && <LoadingSpinner />}
      {!loading && pageError && <ErrorState error={pageError} onRetry={load} />}

      {!loading && !pageError && status && (
        <div className="space-y-4">
          {notice && (
            <div
              role="status"
              className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800"
            >
              {notice}
            </div>
          )}

          <div className="card-base p-6">
            <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="flex items-center gap-2.5">
                  <span
                    aria-hidden="true"
                    className={`h-2.5 w-2.5 rounded-full ${status.connected ? 'bg-emerald-500' : 'bg-slate-300'}`}
                  />
                  <h2 className="text-sm font-semibold text-slate-900">
                    {status.connected ? 'Google Calendar connected' : 'Not connected'}
                  </h2>
                </div>
                <p className="mt-2 max-w-md text-sm leading-relaxed text-slate-500">
                  {status.connected
                    ? 'Synchronization is automatic: new bookings are added to your Google Calendar, reschedules update the event and cancellations remove it.'
                    : 'Connect your Google account so new appointments appear in your calendar automatically. Bookings, reschedules and cancellations stay in sync.'}
                </p>

                {connectError && (
                  <p role="alert" className="mt-3 text-sm font-medium text-red-600">
                    {connectError}
                  </p>
                )}
                {disconnectError && (
                  <p role="alert" className="mt-3 text-sm font-medium text-red-600">
                    {disconnectError}
                  </p>
                )}
              </div>

              {status.connected ? (
                <Button
                  variant="secondary"
                  loading={disconnecting}
                  onClick={() => {
                    setDisconnectError(null)
                    setConfirmOpen(true)
                  }}
                >
                  Disconnect Google Calendar
                </Button>
              ) : (
                <Button loading={connecting} onClick={onConnect}>
                  {connecting ? 'Preparing Google Calendar connection…' : 'Connect Google Calendar'}
                </Button>
              )}
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={confirmOpen}
        onClose={() => (disconnecting ? undefined : setConfirmOpen(false))}
        onConfirm={onDisconnect}
        title="Disconnect Google Calendar?"
        message="Future Veyra appointments will no longer synchronize with your Google Calendar. Events already synced stay in your Google Calendar."
        confirmLabel="Disconnect"
        loading={disconnecting}
      />
    </>
  )
}
