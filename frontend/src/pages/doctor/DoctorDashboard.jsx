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
import { doctorAppointments } from '../../api/appointments'
import { myNotifications } from '../../api/notifications'
import { toLocalDateValue } from '../../components/booking/dates'

const DOCTOR_ACTIONS = [
  { to: '/doctor/appointments', label: 'Appointments', description: 'Full schedule and consultation entry' },
  { to: '/doctor/notifications', label: 'Notifications', description: 'System messages addressed to you' },
  { to: '/doctor/calendar', label: 'Google Calendar', description: 'Keep your schedule in sync' },
]

// Statuses that represent a live appointment — CANCELLED/COMPLETED are never
// surfaced as "upcoming".
const ACTIVE_STATUSES = ['BOOKED', 'RESCHEDULED', 'NO_SHOW']

function byStartAsc(a, b) {
  return new Date(a.appointmentStart).getTime() - new Date(b.appointmentStart).getTime()
}

function isToday(isoInstant) {
  return toLocalDateValue(new Date(isoInstant)) === toLocalDateValue(new Date())
}

export default function DoctorDashboard() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const name = user?.firstName || user?.email?.split('@')[0] || 'Doctor'

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
      setAppointments(await doctorAppointments())
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
    doctorAppointments()
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

  const refreshing = (apptsLoading && appointments !== null) || (notifsLoading && notifications !== null)

  const now = Date.now()
  const list = appointments || []
  // Today's schedule keeps every status so cancellations are visible, not hidden.
  const todays = list.filter((a) => isToday(a.appointmentStart)).sort(byStartAsc)
  const futureActive = list
    .filter(
      (a) => ACTIVE_STATUSES.includes(a.status) && new Date(a.appointmentStart).getTime() > now
    )
    .sort(byStartAsc)
  const next = futureActive[0] || null
  const upcoming = futureActive.slice(1, 6)
  const recentNotifications = [...(notifications || [])]
    .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
    .slice(0, 4)

  const openAppointment = (id) => navigate(`/doctor/appointments/${id}`)

  return (
    <>
      <PortalNotice />
      <PageHeader
        title={`Welcome, Dr. ${name}`}
        subtitle="Your schedule and the visits that need attention."
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

      <QuickActions actions={DOCTOR_ACTIONS} />

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
                <AppointmentCard appointment={next} viewer="doctor" onClick={() => openAppointment(next.id)} />
                <Button variant="secondary" size="sm" onClick={() => openAppointment(next.id)}>
                  Open appointment
                </Button>
              </div>
            ) : (
              <div className="card-base px-4 py-5 text-center">
                <p className="text-sm font-medium text-slate-700">No appointments scheduled</p>
                <p className="mt-1 text-sm text-slate-500">
                  New patient bookings will appear here automatically.
                </p>
                <Button variant="secondary" size="sm" className="mt-3" onClick={() => navigate('/doctor/appointments')}>
                  View appointments
                </Button>
              </div>
            )}
          </section>

          {/* Today's schedule */}
          <section aria-label="Today's appointments">
            <h2 className="mb-3 text-sm font-semibold text-slate-900">Today</h2>
            {apptsLoading && !appointments ? (
              <div className="space-y-3">
                {[0, 1].map((i) => <Skeleton key={i} className="h-20" />)}
              </div>
            ) : apptsError ? (
              <SectionError message="Unable to load appointments." onRetry={loadAppointments} />
            ) : todays.length === 0 ? (
              <p className="card-base px-4 py-4 text-sm text-slate-500">
                No appointments scheduled for today.
              </p>
            ) : (
              <div className="space-y-3">
                {todays.map((a) => (
                  <AppointmentCard
                    key={a.id}
                    appointment={a}
                    viewer="doctor"
                    onClick={() => openAppointment(a.id)}
                  />
                ))}
              </div>
            )}
          </section>

          {/* Upcoming */}
          <section aria-label="Upcoming appointments">
            <div className="mb-3 flex items-center justify-between gap-2">
              <h2 className="text-sm font-semibold text-slate-900">Upcoming</h2>
              <Link
                to="/doctor/appointments"
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
            ) : upcoming.length === 0 ? (
              <p className="card-base px-4 py-4 text-sm text-slate-500">
                Nothing scheduled beyond the appointments above.
              </p>
            ) : (
              <div className="space-y-3">
                {upcoming.map((a) => (
                  <AppointmentCard
                    key={a.id}
                    appointment={a}
                    viewer="doctor"
                    onClick={() => openAppointment(a.id)}
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
          <RecentNotifications notifications={recentNotifications} inboxPath="/doctor/notifications" />
        )}
      </div>
    </>
  )
}
