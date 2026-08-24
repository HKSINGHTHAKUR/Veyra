import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import Button from '../../components/Button'
import StatusBadge from '../../components/StatusBadge'
import LoadingSpinner from '../../components/LoadingSpinner'
import ErrorState from '../../components/ErrorState'
import { ConfirmDialog } from '../../components/Modal'
import {
  adminGetDoctor,
  adminActivateDoctor,
  adminDeactivateDoctor,
} from '../../api/admin'

export default function AdminDoctorDetailPage() {
  const { doctorId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()

  const [doctor, setDoctor] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [toggling, setToggling] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const justCreated = Boolean(location.state?.justCreated)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setDoctor(await adminGetDoctor(doctorId))
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

  async function toggleActive() {
    setToggling(true)
    try {
      const updated = doctor.active
        ? await adminDeactivateDoctor(doctorId)
        : await adminActivateDoctor(doctorId)
      setDoctor(updated)
      setConfirmOpen(false)
    } catch (e) {
      setError(e)
      setConfirmOpen(false)
    } finally {
      setToggling(false)
    }
  }

  if (loading) return <LoadingSpinner fullScreen />
  if (error && !doctor)
    return (
      <>
        <PageHeader title="Doctor" actions={<Button variant="secondary" onClick={() => navigate('/admin/doctors')}>Back</Button>} />
        <ErrorState error={error} onRetry={load} />
      </>
    )

  return (
    <>
      <PageHeader
        title={`Dr. ${doctor.firstName} ${doctor.lastName}`}
        subtitle={doctor.email}
        actions={
          <>
            <Link to={`/admin/doctors/${doctorId}/working-hours`} className="text-sm font-medium text-primary-700 hover:text-primary-800">
              Working hours
            </Link>
            <Link to={`/admin/doctors/${doctorId}/leaves`} className="text-sm font-medium text-primary-700 hover:text-primary-800">
              Leave
            </Link>
            <Button
              variant={doctor.active ? 'secondary' : 'primary'}
              loading={toggling}
              onClick={() => (doctor.active ? setConfirmOpen(true) : toggleActive())}
            >
              {doctor.active ? 'Deactivate' : 'Reactivate'}
            </Button>
          </>
        }
      />

      {justCreated && (
        <p className="mb-4 rounded-lg bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
          Doctor account created. Share the temporary password securely — they should change it after first sign-in.
        </p>
      )}
      {error && (
        <p role="alert" className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-600/20">
          Action failed. Please try again.
        </p>
      )}

      <div className="card-base p-5">
        <dl className="grid gap-x-8 gap-y-4 sm:grid-cols-2">
          <div>
            <dt className="label-base">Status</dt>
            <dd><StatusBadge status={doctor.active ? 'ACTIVE' : 'CANCELLED'} /></dd>
          </div>
          <div>
            <dt className="label-base">Specialisation</dt>
            <dd className="text-sm text-slate-800">{doctor.specialisation}</dd>
          </div>
          <div>
            <dt className="label-base">Phone</dt>
            <dd className="text-sm text-slate-800">{doctor.phone || '—'}</dd>
          </div>
          <div>
            <dt className="label-base">Consultation fee</dt>
            <dd className="text-sm text-slate-800">
              {typeof doctor.consultationFee === 'number' ? `$${Number(doctor.consultationFee).toFixed(2)}` : '—'}
            </dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="label-base">Bio</dt>
            <dd className="text-sm leading-relaxed text-slate-600">{doctor.bio || '—'}</dd>
          </div>
        </dl>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={toggleActive}
        title="Deactivate this doctor?"
        message="They will disappear from patient search and their availability becomes empty. Existing appointments are not deleted."
        confirmLabel="Deactivate"
        loading={toggling}
      />
    </>
  )
}
