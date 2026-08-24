import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import AppointmentCard from '../../components/AppointmentCard'
import LoadingSpinner, { Skeleton } from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import { patientAppointments } from '../../api/appointments'

export default function PatientAppointmentsPage() {
  const navigate = useNavigate()
  const [appointments, setAppointments] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setAppointments(await patientAppointments())
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
      <PageHeader
        title="My appointments"
        subtitle="Newest first. Select an appointment for details and actions."
      />

      {loading && !appointments && (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      )}
      {loading && appointments && <LoadingSpinner />}

      {!loading && error && <ErrorState error={error} onRetry={load} />}

      {!loading && !error && appointments?.length === 0 && (
        <EmptyState
          icon="📅"
          title="No appointments yet"
          description="When you book with a doctor, your visits will appear here."
        />
      )}

      {!loading && !error && appointments?.length > 0 && (
        <div className="space-y-3">
          {appointments.map((a) => (
            <AppointmentCard
              key={a.id}
              appointment={a}
              viewer="patient"
              onClick={() => navigate(`/patient/appointments/${a.id}`)}
            />
          ))}
        </div>
      )}
    </>
  )
}
