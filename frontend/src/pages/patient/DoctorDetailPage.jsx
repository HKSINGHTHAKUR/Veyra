import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import Button from '../../components/Button'
import { Skeleton } from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import BookingPanel from '../../components/booking/BookingPanel'
import { getDoctor } from '../../api/doctors'
import { toApiError } from '../../api/client'

function initials(first = '', last = '') {
  return `${first?.[0] ?? ''}${last?.[0] ?? ''}`.toUpperCase() || 'Dr'
}

export default function DoctorDetailPage() {
  const { doctorId } = useParams()
  const navigate = useNavigate()
  const [doctor, setDoctor] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setDoctor(await getDoctor(doctorId))
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doctorId])

  const notFound = !loading && error && toApiError(error).status === 404

  return (
    <>
      <PageHeader
        title="Doctor profile"
        subtitle="Check availability and book an appointment."
        actions={
          <Button variant="secondary" onClick={() => navigate('/patient/doctors')}>
            Back to search
          </Button>
        }
      />

      {loading && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
          <Skeleton className="h-48" />
          <Skeleton className="h-72" />
        </div>
      )}

      {!loading && notFound && (
        <EmptyState
          icon="🩺"
          title="Doctor not found"
          description="This doctor no longer exists or is no longer listed on Veyra."
          action={
            <Button variant="secondary" size="sm" onClick={() => navigate('/patient/doctors')}>
              Back to search
            </Button>
          }
        />
      )}

      {!loading && error && !notFound && <ErrorState error={error} onRetry={load} />}

      {!loading && !error && doctor && (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
          <section className="card-base p-5" aria-label="Doctor profile">
            <div className="flex items-start gap-4">
              <div
                aria-hidden="true"
                className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-primary-50 text-sm font-semibold text-primary-700"
              >
                {initials(doctor.firstName, doctor.lastName)}
              </div>
              <div className="min-w-0">
                <h2 className="text-base font-semibold text-slate-900">
                  Dr. {doctor.firstName} {doctor.lastName}
                </h2>
                <p className="mt-0.5 text-xs font-medium uppercase tracking-wide text-primary-700">
                  {doctor.specialisation}
                </p>
              </div>
            </div>
            {doctor.bio && <p className="mt-4 text-sm leading-relaxed text-slate-600">{doctor.bio}</p>}
            {Number(doctor.consultationFee) > 0 && (
              <p className="mt-4 border-t border-slate-100 pt-4 text-sm font-medium text-slate-700">
                ${Number(doctor.consultationFee).toFixed(2)}
                <span className="ml-1 text-xs font-normal text-slate-400">/ consultation</span>
              </p>
            )}
          </section>

          <BookingPanel doctor={doctor} />
        </div>
      )}
    </>
  )
}
