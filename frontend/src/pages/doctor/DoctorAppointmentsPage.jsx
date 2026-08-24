import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import AppointmentCard from '../../components/AppointmentCard'
import LoadingSpinner, { Skeleton } from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import { doctorAppointments } from '../../api/appointments'

export default function DoctorAppointmentsPage() {
  const navigate = useNavigate()
  const [appointments, setAppointments] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setAppointments(await doctorAppointments())
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  return (
    <>
      <PageHeader title="Appointments" subtitle="Your visits, ordered by start time." />

      {loading && !appointments && (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-24" />)}
        </div>
      )}
      {loading && appointments && <LoadingSpinner />}

      {!loading && error && <ErrorState error={error} onRetry={load} />}

      {!loading && !error && appointments?.length === 0 && (
        <EmptyState
          icon="📅"
          title="No appointments yet"
          description="When patients book with you, the visits will appear here."
        />
      )}

      {!loading && !error && appointments?.length > 0 && (
        <div className="space-y-3">
          {appointments.map((a) => (
            <AppointmentCard
              key={a.id}
              appointment={a}
              viewer="doctor"
              onClick={() => navigate(`/doctor/appointments/${a.id}`)}
            />
          ))}
        </div>
      )}
    </>
  )
}
