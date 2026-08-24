import { Link, useSearchParams } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import { useAuth } from '../../context/AuthContext'

/**
 * Landing route after Google redirects back through the backend callback.
 * The backend only 302s here with ?status=success; failures surface as a
 * backend error page before this route is reached. Anything that is not
 * "success" is therefore treated honestly as not-connected — this page never
 * validates OAuth state itself (the backend already consumed it server-side).
 */
export default function CalendarConnectedPage() {
  const [params] = useSearchParams()
  const { role } = useAuth()
  const status = params.get('status')
  const success = status === 'success'
  const failure = status === 'error' || status === 'denied' || status === 'failed'

  const calendarPath = role === 'DOCTOR' ? '/doctor/calendar' : '/patient/calendar'

  return (
    <>
      <PageHeader title="Google Calendar" />
      <div className="card-base p-8 text-center">
        <div
          aria-hidden="true"
          className={`mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full text-xl ${
            success ? 'bg-emerald-50' : 'bg-red-50'
          }`}
        >
          {success ? '✅' : '⚠️'}
        </div>
        <h1 className="text-base font-semibold text-slate-900">
          {success
            ? 'Google Calendar connected'
            : failure
              ? 'Google Calendar could not be connected.'
              : 'Google Calendar connection status is unknown.'}
        </h1>
        <p className="mx-auto mt-2 max-w-sm text-sm text-slate-500">
          {success
            ? 'New appointments will be added to your Google Calendar automatically. Reschedules and cancellations stay in sync.'
            : failure
              ? 'No changes were made. You can try again from the calendar page.'
              : 'Please check the calendar page to see your current connection status.'}
        </p>
        <Link
          to={calendarPath}
          className="mt-6 inline-flex h-10 items-center rounded-lg bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700"
        >
          {success ? 'Continue to Veyra' : 'Go to calendar settings'}
        </Link>
      </div>
    </>
  )
}
