import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import Button from '../../components/Button'
import StatusBadge from '../../components/StatusBadge'
import { Skeleton } from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import CancelDialog from '../../components/appointments/CancelDialog'
import RescheduleDialog from '../../components/appointments/RescheduleDialog'
import PreVisitSummaryCard from '../../components/ai/PreVisitSummaryCard'
import PostVisitSummaryCard from '../../components/ai/PostVisitSummaryCard'
import { cancelAppointment, getAppointment } from '../../api/appointments'
import { getConsultation } from '../../api/consultation'
import { getDoctor } from '../../api/doctors'
import { toApiError } from '../../api/client'
import { formatSlotDateTime } from '../../components/booking/dates'

// Statuses where the backend still permits patient actions
const ACTIONABLE_STATUSES = ['BOOKED', 'RESCHEDULED', 'NO_SHOW']

function DetailRow({ label, children }) {
  return (
    <div className="flex flex-col gap-0.5 px-4 py-2.5 sm:flex-row sm:items-start sm:gap-4">
      <dt className="w-40 shrink-0 text-slate-500">{label}</dt>
      <dd className="min-w-0 flex-1 font-medium text-slate-900">{children}</dd>
    </div>
  )
}

function durationMinutes(start, end) {
  if (!start || !end) return null
  return Math.round((new Date(end).getTime() - new Date(start).getTime()) / 60000)
}

export default function PatientAppointmentDetailPage() {
  const { appointmentId } = useParams()
  const navigate = useNavigate()
  const [appointment, setAppointment] = useState(null)
  const [doctor, setDoctor] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelLoading, setCancelLoading] = useState(false)
  const [cancelError, setCancelError] = useState(null)
  const [rescheduleOpen, setRescheduleOpen] = useState(false)
  const [notice, setNotice] = useState(null)
  const [consultation, setConsultation] = useState(null)
  const [consultationLoaded, setConsultationLoaded] = useState(false)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setAppointment(await getAppointment(appointmentId))
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appointmentId])

  // AppointmentResponse carries no doctor name — fetch the real summary once.
  const doctorId = appointment?.doctorId
  useEffect(() => {
    if (!doctorId) return undefined
    let active = true
    getDoctor(doctorId)
      .then((d) => {
        if (active) setDoctor(d)
      })
      .catch(() => {
        if (active) setDoctor(null)
      })
    return () => {
      active = false
    }
  }, [doctorId])

  // Post-visit content only exists once the doctor completed a consultation
  useEffect(() => {
    let active = true
    setConsultationLoaded(false)
    getConsultation(appointmentId)
      .then((c) => {
        if (active) setConsultation(c)
      })
      .catch(() => {
        if (active) setConsultation(null)
      })
      .finally(() => {
        if (active) setConsultationLoaded(true)
      })
    return () => {
      active = false
    }
  }, [appointmentId])

  const notFound = !loading && error && toApiError(error).status === 404

  const status = appointment?.status
  const isPast = appointment && new Date(appointment.appointmentStart).getTime() < Date.now()
  const actionsAvailable =
    appointment && ACTIONABLE_STATUSES.includes(status) && !isPast

  const doctorName = doctor
    ? `Dr. ${doctor.firstName} ${doctor.lastName}`
    : appointment?.doctorEmail
      ? `Dr. ${appointment.doctorEmail.split('@')[0]}`
      : '—'

  async function onCancel(reason) {
    setCancelLoading(true)
    setCancelError(null)
    try {
      const updated = await cancelAppointment(appointmentId, reason)
      setAppointment(updated)
      setCancelOpen(false)
      setNotice('Appointment cancelled.')
    } catch (e) {
      const { status: errStatus } = toApiError(e)
      if (errStatus === 403) {
        setCancelError('This appointment cannot be cancelled from this account.')
      } else if (!e.response) {
        setCancelError('Unable to reach the server. Check your connection and try again.')
      } else {
        setCancelError(toApiError(e).message)
      }
      // 409 usually means the state changed underneath us — resync silently
      if (errStatus === 409) load()
    } finally {
      setCancelLoading(false)
    }
  }

  function onRescheduled(updated) {
    setAppointment(updated)
    setRescheduleOpen(false)
    setNotice(`Appointment rescheduled to ${formatSlotDateTime(updated.appointmentStart)}.`)
  }

  return (
    <>
      <PageHeader
        title="Appointment details"
        subtitle={appointment?.doctorSpecialisation}
        actions={
          <Button variant="secondary" onClick={() => navigate('/patient/appointments')}>
            Back to appointments
          </Button>
        }
      />

      {loading && (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          <Skeleton className="h-72" />
          <Skeleton className="h-40" />
        </div>
      )}

      {!loading && notFound && (
        <EmptyState
          icon="🧾"
          title="Appointment not found"
          description="This appointment does not exist or is not visible to your account."
          action={
            <Button variant="secondary" size="sm" onClick={() => navigate('/patient/appointments')}>
              Back to appointments
            </Button>
          }
        />
      )}

      {!loading && error && !notFound && <ErrorState error={error} onRetry={load} />}

      {!loading && !error && appointment && (
        <>
          {notice && (
            <div
              role="status"
              className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800"
            >
              {notice}
            </div>
          )}

          <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
            <section className="card-base p-5" aria-label="Appointment information">
              <div className="mb-3 flex items-center justify-between gap-3">
                <h2 className="text-sm font-semibold text-slate-900">Appointment</h2>
                <StatusBadge status={status} />
              </div>

              <dl className="divide-y divide-slate-100 rounded-lg border border-slate-200 text-sm">
                <DetailRow label="Doctor">
                  {doctorName}
                  {appointment.doctorSpecialisation && (
                    <span className="font-normal text-slate-500">
                      {' '}
                      · {appointment.doctorSpecialisation}
                    </span>
                  )}
                </DetailRow>
                <DetailRow label="Date &amp; time">
                  {formatSlotDateTime(appointment.appointmentStart)}
                </DetailRow>
                <DetailRow label="Duration">
                  {durationMinutes(appointment.appointmentStart, appointment.appointmentEnd) ?? '—'}{' '}
                  min
                </DetailRow>
                <DetailRow label="Patient">{appointment.patientEmail}</DetailRow>
                <DetailRow label="Booked at">{formatSlotDateTime(appointment.bookedAt)}</DetailRow>
                <DetailRow label="Appointment ID">
                  <span className="break-all font-mono text-xs font-normal text-slate-500">
                    {appointment.id}
                  </span>
                </DetailRow>
              </dl>

              {appointment.symptomText && (
                <div className="mt-4 rounded-lg bg-slate-50 px-4 py-3">
                  <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
                    Reason for visit
                  </p>
                  <p className="mt-1 whitespace-pre-wrap text-sm text-slate-700">
                    {appointment.symptomText}
                  </p>
                </div>
              )}
            </section>

            <section className="card-base p-5" aria-label="Appointment actions">
              <h2 className="mb-3 text-sm font-semibold text-slate-900">Actions</h2>

              {actionsAvailable && (
                <>
                  <div className="flex flex-col gap-2">
                    <Button variant="secondary" onClick={() => setRescheduleOpen(true)}>
                      Reschedule
                    </Button>
                    <Button
                      variant="danger"
                      onClick={() => {
                        setCancelError(null)
                        setCancelOpen(true)
                      }}
                    >
                      Cancel appointment
                    </Button>
                  </div>
                  <p className="mt-3 text-xs text-slate-400">
                    Cancellation cannot be undone. Rescheduling keeps your symptoms attached.
                  </p>
                </>
              )}

              {status === 'CANCELLED' && (
                <p className="text-sm text-slate-500">
                  This appointment has been cancelled. No further actions are available.
                </p>
              )}

              {status === 'COMPLETED' && (
                <p className="text-sm text-slate-500">
                  This appointment has been completed. Actions are no longer available.
                </p>
              )}

              {status === 'NO_SHOW' && isPast && (
                <p className="text-sm text-slate-500">
                  This appointment was missed. No further actions are available.
                </p>
              )}

              {!['CANCELLED', 'COMPLETED'].includes(status) && status !== 'NO_SHOW' && isPast && (
                <p className="text-sm text-slate-500">
                  This appointment time has passed, so cancellation and rescheduling are no longer
                  available.
                </p>
              )}
            </section>
          </div>

          <div className="mt-6 space-y-6">
            <PreVisitSummaryCard appointmentId={appointmentId} canGenerate />
            {consultationLoaded && consultation && (
              <PostVisitSummaryCard appointmentId={appointmentId} />
            )}
          </div>

          <CancelDialog
            open={cancelOpen}
            onClose={() => setCancelOpen(false)}
            onConfirm={onCancel}
            loading={cancelLoading}
            error={cancelError}
          />

          <RescheduleDialog
            open={rescheduleOpen}
            onClose={() => setRescheduleOpen(false)}
            appointment={appointment}
            onRescheduled={onRescheduled}
          />
        </>
      )}
    </>
  )
}
