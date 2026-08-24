import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import PortalNotice from '../../components/PortalNotice'
import Button from '../../components/Button'
import StatusBadge from '../../components/StatusBadge'
import { Skeleton } from '../../components/LoadingSpinner'
import QuickActions from '../../components/dashboard/QuickActions'
import SectionError from '../../components/dashboard/SectionError'
import { adminListDoctors } from '../../api/admin'

const ADMIN_ACTIONS = [
  { to: '/admin/doctors', label: 'Manage doctors', description: 'Accounts, activation, hours and leaves' },
  { to: '/admin/doctors/new', label: 'Add doctor', description: 'Create a new doctor account' },
]

/**
 * Operational admin landing page. Counts are derived directly from the real
 * doctor list — no growth/performance metrics exist in the backend, so none
 * are shown.
 */
export default function AdminDashboard() {
  const navigate = useNavigate()
  const [doctors, setDoctors] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

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

  const list = doctors || []
  const activeCount = list.filter((d) => d.active).length

  return (
    <>
      <PortalNotice />
      <PageHeader
        title="Administration"
        subtitle="Manage doctors, working hours and leave."
        actions={
          <Button variant="secondary" size="sm" loading={loading && doctors !== null} onClick={load}>
            Refresh
          </Button>
        }
      />

      <QuickActions actions={ADMIN_ACTIONS} />

      <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        {/* Doctor overview */}
        <section aria-label="Doctor overview">
          <div className="mb-3 flex items-center justify-between gap-2">
            <h2 className="text-sm font-semibold text-slate-900">Doctors</h2>
            <Link
              to="/admin/doctors"
              className="text-xs font-semibold text-primary-700 hover:text-primary-800"
            >
              Manage doctors
            </Link>
          </div>

          {loading && !doctors ? (
            <div className="space-y-3">
              {[0, 1, 2].map((i) => <Skeleton key={i} className="h-16" />)}
            </div>
          ) : error ? (
            <SectionError message="Unable to load doctors." onRetry={load} />
          ) : list.length === 0 ? (
            <div className="card-base px-4 py-5 text-center">
              <p className="text-sm font-medium text-slate-700">No doctors found</p>
              <p className="mt-1 text-sm text-slate-500">
                Create the first doctor account to start accepting appointments.
              </p>
              <Button size="sm" className="mt-3" onClick={() => navigate('/admin/doctors/new')}>
                Add doctor
              </Button>
            </div>
          ) : (
            <ul className="card-base divide-y divide-slate-100">
              {list.slice(0, 5).map((d) => (
                <li key={d.id}>
                  <Link
                    to={`/admin/doctors/${d.id}`}
                    className="flex items-center justify-between gap-3 px-4 py-3 transition-colors hover:bg-slate-50"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-slate-900">
                        Dr. {d.firstName} {d.lastName}
                      </p>
                      <p className="truncate text-xs text-slate-500">{d.specialisation}</p>
                    </div>
                    <StatusBadge status={d.active ? 'ACTIVE' : 'CANCELLED'} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Doctor counts — directly derivable from the real list */}
        <section className="card-base p-5" aria-label="Doctor counts">
          <h2 className="mb-3 text-sm font-semibold text-slate-900">Doctor accounts</h2>
          {loading && !doctors ? (
            <Skeleton className="h-24" />
          ) : error ? (
            <SectionError message="Unable to load doctors." onRetry={load} />
          ) : (
            <dl className="space-y-3">
              <div className="flex items-center justify-between">
                <dt className="text-sm text-slate-500">Total doctors</dt>
                <dd className="text-sm font-semibold text-slate-900">{list.length}</dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-sm text-slate-500">Active doctors</dt>
                <dd className="text-sm font-semibold text-emerald-700">{activeCount}</dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-sm text-slate-500">Inactive doctors</dt>
                <dd className="text-sm font-semibold text-slate-600">{list.length - activeCount}</dd>
              </div>
            </dl>
          )}
          <p className="mt-4 border-t border-slate-100 pt-3 text-xs text-slate-400">
            Working hours and leaves are managed from each doctor&apos;s page.
          </p>
        </section>
      </div>
    </>
  )
}
