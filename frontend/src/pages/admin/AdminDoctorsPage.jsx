import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import Button from '../../components/Button'
import LoadingSpinner, { Skeleton } from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import StatusBadge from '../../components/StatusBadge'
import { adminListDoctors } from '../../api/admin'

/** Admin doctor list — full detail (email, status) unlike the patient search view. */
export default function AdminDoctorsPage() {
  const navigate = useNavigate()
  const [doctors, setDoctors] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setDoctors(await adminListDoctors())
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
        title="Doctors"
        subtitle="All doctor accounts, including deactivated ones."
        actions={
          <Button onClick={() => navigate('/admin/doctors/new')}>Add doctor</Button>
        }
      />

      {loading && !doctors && (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-16" />)}
        </div>
      )}
      {loading && doctors && <LoadingSpinner />}

      {!loading && error && <ErrorState error={error} onRetry={load} />}

      {!loading && !error && doctors?.length === 0 && (
        <EmptyState
          icon="🩺"
          title="No doctors yet"
          description="Create the first doctor account to start accepting appointments."
          action={<Button onClick={() => navigate('/admin/doctors/new')}>Add doctor</Button>}
        />
      )}

      {!loading && !error && doctors?.length > 0 && (
        <>
          {/* Table on md+ */}
          <div className="card-base hidden overflow-hidden md:block">
            <table className="min-w-full divide-y divide-slate-200">
              <thead className="bg-slate-50">
                <tr>
                  <th className="table-head-cell">Doctor</th>
                  <th className="table-head-cell">Email</th>
                  <th className="table-head-cell">Specialisation</th>
                  <th className="table-head-cell">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {doctors.map((d) => (
                  <tr
                    key={d.id}
                    tabIndex={0}
                    onClick={() => navigate(`/admin/doctors/${d.id}`)}
                    onKeyDown={(e) => e.key === 'Enter' && navigate(`/admin/doctors/${d.id}`)}
                    className="cursor-pointer hover:bg-slate-50"
                  >
                    <td className="table-cell font-medium text-slate-900">
                      Dr. {d.firstName} {d.lastName}
                    </td>
                    <td className="table-cell">{d.email}</td>
                    <td className="table-cell">{d.specialisation}</td>
                    <td className="table-cell">
                      <StatusBadge status={d.active ? 'ACTIVE' : 'CANCELLED'} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Cards on small screens */}
          <div className="space-y-3 md:hidden">
            {doctors.map((d) => (
              <div
                key={d.id}
                role="button"
                tabIndex={0}
                onClick={() => navigate(`/admin/doctors/${d.id}`)}
                onKeyDown={(e) => e.key === 'Enter' && navigate(`/admin/doctors/${d.id}`)}
                className="card-base cursor-pointer p-4 hover:shadow-pop"
              >
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-semibold text-slate-900">Dr. {d.firstName} {d.lastName}</p>
                  <StatusBadge status={d.active ? 'ACTIVE' : 'CANCELLED'} />
                </div>
                <p className="mt-1 truncate text-sm text-slate-500">{d.email}</p>
                <p className="mt-0.5 text-xs text-primary-700">{d.specialisation}</p>
              </div>
            ))}
          </div>
        </>
      )}
    </>
  )
}
