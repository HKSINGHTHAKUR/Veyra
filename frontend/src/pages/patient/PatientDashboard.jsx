import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import PageHeader from '../../components/PageHeader'
import PortalNotice from '../../components/PortalNotice'
import Button from '../../components/Button'
import AppointmentCard from '../../components/AppointmentCard'
import { Skeleton } from '../../components/LoadingSpinner'
import QuickActions from '../../components/dashboard/QuickActions'
import SectionError from '../../components/dashboard/SectionError'
import RecentNotifications from '../../components/dashboard/RecentNotifications'
import { patientAppointments } from '../../api/appointments'
import { myNotifications } from '../../api/notifications'

const PATIENT_ACTIONS = [
  { to: '/patient/doctors', label: 'Find a doctor', description: 'Browse specialists and live availability' },
  { to: '/patient/appointments', label: 'My appointments', description: 'Review, reschedule or cancel visits' },
  { to: '/patient/notifications', label: 'Notifications', description: 'Confirmations and reminders' },
  { to: '/patient/calendar', label: 'Google Calendar', description: 'Keep visits in sync automatically' },
]

// Statuses that represent a live appointment (mirrors backend semantics;
// CANCELLED and COMPLETED are never "upcoming").
const ACTIVE_STATUSES = ['BOOKED', 'RESCHEDULED', 'NO_SHOW']

function byStartAsc(a, b) {
  return new Date(a.appointmentStart).getTime() - new Date(b.appointmentStart).getTime()
}

export default function PatientDashboard() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const firstName = user?.firstName || user?.email?.split('@')[0] || 'there'

  const [appointments, setAppointments] = useState(null)
  const [apptsLoading, setApptsLoading] = useState(true)
  const [apptsError, setApptsError] = useState(null)

  const [notifications, setNotifications] = useState(null)
  const [notifsLoading, setNotifsLoading] = useState(true)
  const [notifsError, setNotifsError] = useState(null)

  async function loadAppointments() {
    setApptsLoading(true)
    setApptsError(null)
    try {
      setAppointments(await patientAppointments())
    } catch (e) {
      setApptsError(e)
    } finally {
      setApptsLoading(false)
    }
  }

  async function loadNotifications() {
    setNotifsLoading(true)
    setNotifsError(null)
    try {
      setNotifications(await myNotifications())
    } catch (e) {
      setNotifsError(e)
    } finally {
      setNotifsLoading(false)
    }
  }

  useEffect(() => {
    let active = true
    setApptsLoading(true)
    patientAppointments()
      .then((data) => active && setAppointments(data))
      .catch((e) => active && setApptsError(e))
      .finally(() => active && setApptsLoading(false))

    setNotifsLoading(true)
    myNotifications()
      .then((data) => active && setNotifications(data))
      .catch((e) => active && setNotifsError(e))
      .finally(() => active && setNotifsLoading(false))

    return () => {
      active = false
    }
  }, [])

  // Independent section-level refresh (no polling by design).
  const refreshing = (apptsLoading && appointments !== null) || (notifsLoading && notifications !== null)

  const now = Date.now()
  const upcoming = (appointments || [])
    .filter(
      (a) => ACTIVE_STATUSES.includes(a.status) && new Date(a.appointmentStart).getTime() > now
    )
    .sort(byStartAsc)
  const next = upcoming[0] || null
  const recent = [...(appointments || [])].sort(byStartAsc).reverse().slice(0, 5)
  const recentNotifications = [...(notifications || [])]
    .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
    .slice(0, 4)

  return (
    <>
      <PortalNotice />
      <PageHeader
        title={`Welcome, ${firstName}`}
        subtitle="Manage your appointments and stay on top of your care."
        actions={
          <Button
            variant="secondary"
            size="sm"
            loading={refreshing}
            onClick={() => {
              loadAppointments()
              loadNotifications()
            }}
          >
            Refresh
          </Button>
        }
      />

      <QuickActions actions={PATIENT_ACTIONS} />

      <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="space-y-6">
          {/* Next appointment */}
          <section aria-label="Next appointment">
            <h2 className="mb-3 text-sm font-semibold text-slate-900">Next appointment</h2>
            {apptsLoading && !appointments ? (
              <Skeleton className="h-28" />
            ) : apptsError ? (
              <SectionError message="Unable to load appointments." onRetry={loadAppointments} />
            ) : next ? (
              <div className="space-y-2">
                <AppointmentCard
                  appointment={next}
                  viewer="patient"
                  onClick={() => navigate(`/patient/appointments/${next.id}`)}
                />
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => navigate(`/patient/appointments/${next.id}`)}
                >
                  View appointment
                </Button>
              </div>
            ) : (
              <div className="card-base px-4 py-5 text-center">
                <p className="text-sm font-medium text-slate-700">No upcoming appointments</p>
                <p className="mt-1 text-sm text-slate-500">
                  Book a visit with one of our specialists when it suits you.
                </p>
                <Button size="sm" className="mt-3" onClick={() => navigate('/patient/doctors')}>
                  Find a doctor
                </Button>
              </div>
            )}
          </section>

          {/* Recent appointments */}
          <section aria-label="Recent appointments">
            <div className="mb-3 flex items-center justify-between gap-2">
              <h2 className="text-sm font-semibold text-slate-900">Recent appointments</h2>
              <Link
                to="/patient/appointments"
                className="text-xs font-semibold text-primary-700 hover:text-primary-800"
              >
                View all appointments
              </Link>
            </div>
            {apptsLoading && !appointments ? (
              <div className="space-y-3">
                {[0, 1].map((i) => <Skeleton key={i} className="h-20" />)}
              </div>
            ) : apptsError ? (
              <SectionError message="Unable to load appointments." onRetry={loadAppointments} />
            ) : recent.length === 0 ? (
              <p className="card-base px-4 py-4 text-sm text-slate-500">
                No appointments yet. Your booking history will appear here.
              </p>
            ) : (
              <div className="space-y-3">
                {recent.map((a) => (
                  <AppointmentCard
                    key={a.id}
                    appointment={a}
                    viewer="patient"
                    onClick={() => navigate(`/patient/appointments/${a.id}`)}
                  />
                ))}
              </div>
            )}
          </section>
        </div>

        {/* Notifications preview */}
        {notifsLoading && !notifications ? (
          <Skeleton className="h-56" />
        ) : notifsError ? (
          <SectionError message="Unable to load notifications." onRetry={loadNotifications} />
        ) : (
          <RecentNotifications notifications={recentNotifications} inboxPath="/patient/notifications" />
        )}
      </div>
    </>
  )
}
