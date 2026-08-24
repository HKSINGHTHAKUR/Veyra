import { useAuth } from '../context/AuthContext'

export default function UnauthorizedPage() {
  const { role } = useAuth()
  return (
    <div className="flex min-h-screen items-center justify-center bg-surface p-6">
      <div className="card-base max-w-md p-8 text-center">
        <div aria-hidden="true" className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-xl">
          🔒
        </div>
        <h1 className="text-base font-semibold text-slate-900">Access denied</h1>
        <p className="mt-2 text-sm text-slate-500">
          Your account{role ? ` (${role.toLowerCase()})` : ''} does not have permission to view
          that page. If you believe this is a mistake, contact your practice administrator.
        </p>
      </div>
    </div>
  )
}
