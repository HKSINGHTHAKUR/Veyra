import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import Button from '../../components/Button'
import StatusBadge from '../../components/StatusBadge'
import { Skeleton } from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import PreVisitSummaryCard from '../../components/ai/PreVisitSummaryCard'
import PostVisitSummaryCard from '../../components/ai/PostVisitSummaryCard'
import ConsultationWorkspace from '../../components/consultation/ConsultationWorkspace'
import { getAppointment } from '../../api/appointments'
import { getConsultation } from '../../api/consultation'
import { toApiError } from '../../api/client'
import { formatSlotDateTime } from '../../components/booking/dates'

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

/**
 * Doctor view of one appointment: appointment facts, patient symptoms,
 * pre-visit AI, the consultation workspace and post-visit AI.
 */
export default function DoctorAppointmentDetailPage() {
  const { appointmentId } = useParams()
  const navigate = useNavigate()
  const [appointment, setAppointment] = useState(null)
  const [consultation, setConsultation] = useState(null)
  const [consultationLoading, setConsultationLoading] = useState(true)
  const [justCompleted, setJustCompleted] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

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

  useEffect(() => {
    let active = true
    setConsultationLoading(true)
    getConsultation(appointmentId)
      .then((c) => {
        if (active) setConsultation(c)
      })
      .catch(() => {
        if (active) setConsultation(null)
      })
      .finally(() => {
        if (active) setConsultationLoading(false)
      })
    return () => {
      active = false
    }
  }, [appointmentId])

  const notFound = !loading && error && toApiError(error).status === 404

  return (
    <>
      <PageHeader
        title="Appointment"
        subtitle={appointment ? formatSlotDateTime(appointment.appointmentStart) : undefined}
        actions={
          <Button variant="secondary" onClick={() => navigate('/doctor/appointments')}>
            Back to schedule
          </Button>
        }
      />

      {loading && (
        <div className="space-y-6">
          <Skeleton className="h-56" />
          <Skeleton className="h-40" />
        </div>
      )}

      {!loading && notFound && (
        <EmptyState
          icon="🧾"
          title="Appointment not found"
          description="This appointment does not exist or is not visible to your account."
          action={
            <Button variant="secondary" size="sm" onClick={() => navigate('/doctor/appointments')}>
              Back to schedule
            </Button>
          }
        />
      )}

      {!loading && error && !notFound && <ErrorState error={error} onRetry={load} />}

      {!loading && !error && appointment && (
        <div className="space-y-6">
          {appointment.status === 'CANCELLED' && (
            <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
              This appointment has been cancelled.
            </div>
          )}

          <section className="card-base p-5" aria-label="Appointment information">
            <div className="mb-3 flex items-center justify-between gap-3">
              <h2 className="text-sm font-semibold text-slate-900">Appointment</h2>
              <StatusBadge status={appointment.status} />
            </div>
            <dl className="divide-y divide-slate-100 rounded-lg border border-slate-200 text-sm">
              <DetailRow label="Patient">{appointment.patientEmail}</DetailRow>
              <DetailRow label="Date &amp; time">
                {formatSlotDateTime(appointment.appointmentStart)}
              </DetailRow>
              <DetailRow label="Duration">
                {durationMinutes(appointment.appointmentStart, appointment.appointmentEnd) ?? '—'}{' '}
                min
              </DetailRow>
              <DetailRow label="Booked at">{formatSlotDateTime(appointment.bookedAt)}</DetailRow>
              <DetailRow label="Appointment ID">
                <span className="break-all font-mono text-xs font-normal text-slate-500">
                  {appointment.id}
                </span>
              </DetailRow>
            </dl>
          </section>

          <section className="card-base p-5" aria-label="Patient symptoms">
            <h2 className="mb-3 text-sm font-semibold text-slate-900">
              Symptoms / reason for visit
            </h2>
            {appointment.symptomText ? (
              <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
                {appointment.symptomText}
              </p>
            ) : (
              <p className="text-sm text-slate-500">The patient did not provide symptoms.</p>
            )}
          </section>

          <PreVisitSummaryCard appointmentId={appointmentId} />

          {justCompleted && (
            <div
              role="status"
              className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800"
            >
              Consultation completed.
            </div>
          )}

          <ConsultationWorkspace
            appointmentId={appointmentId}
            appointmentStatus={appointment.status}
            consultation={consultation}
            consultationLoading={consultationLoading}
            onCompleted={(c) => {
              setConsultation(c)
              setJustCompleted(true)
            }}
          />

          {consultation && (
            <PostVisitSummaryCard appointmentId={appointmentId} canGenerate />
          )}
        </div>
      )}
    </>
  )
}
