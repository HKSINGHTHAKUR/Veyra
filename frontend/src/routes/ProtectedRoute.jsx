import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from '../components/LoadingSpinner'

/**
 * Route guard for UX only — the backend remains the real security boundary.
 *
 * - Not authenticated            -> /login (remembering where the user wanted to go)
 * - Authenticated but wrong role -> /403 (explicit unauthorized state; never a
 *                                   silent redirect that hides misconfiguration)
 *
 * Usage: <ProtectedRoute allow={['PATIENT']} ... />
 */
export default function ProtectedRoute({ allow = [], children }) {
  const { isAuthenticated, role } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (allow.length > 0 && !allow.includes(role)) {
    return <Navigate to="/403" replace />
  }

  // Hydration is instant from localStorage; keep a guard anyway for safety.
  if (!role) return <LoadingSpinner fullScreen label="Restoring session…" />

  return children
}
