import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import {
  clearSession,
  getStoredToken,
  getStoredUser,
  storeSession,
} from '../api/client'
import * as authApi from '../api/auth'

const AuthContext = createContext(null)

/**
 * Decodes the payload of a JWT without verifying its signature.
 * Used only for a coarse client-side sanity/expiry check during hydration;
 * the backend remains the real security boundary.
 */
function decodeJwtPayload(token) {
  try {
    const part = token.split('.')[1]
    if (!part) return null
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const payload = JSON.parse(atob(padded))
    return payload && typeof payload === 'object' ? payload : null
  } catch {
    return null
  }
}

/**
 * A stale/invalid token (unparseable or already expired) must not leave the app
 * in a false authenticated state. Anything we cannot parse, or that has
 * expired, is cleared immediately on hydration. Signature verification remains
 * a backend concern; invalid signatures surface as a 401 on the first API call.
 */
function isTokenUsable(token) {
  if (!token) return false
  const payload = decodeJwtPayload(token)
  if (!payload) return false
  if (typeof payload.exp !== 'number') return false
  return payload.exp * 1000 > Date.now()
}

/**
 * Auth foundation for the SPA.
 *
 * - JWT + user profile persisted in localStorage (appropriate for a stateless
 *   JWT SPA; the backend remains the real security boundary).
 * - `login` / `register` call the backend, store the session and expose
 *   { user, token, role }.
 * - `login(email, password, expectedRole)` enforces strict portal-role
 *   matching: when `expectedRole` is supplied and does not match the role
 *   returned by the backend, the login is rejected and NO session is stored.
 * - `logout` clears local state. No server-side invalidation exists (stateless
 *   JWT), so this is client-side only by design.
 * - On mount the session is hydrated from storage; role-scoped /me endpoints
 *   are used opportunistically to refresh the profile when available.
 */
export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => {
    const stored = getStoredToken()
    if (stored && !isTokenUsable(stored)) {
      clearSession()
      return null
    }
    return stored
  })
  const [user, setUser] = useState(() => getStoredUser())

  const role = user?.role || null

  const logout = useCallback(() => {
    clearSession()
    setToken(null)
    setUser(null)
  }, [])

  // Log the user out when an expired token triggers a session expiry event
  // from the Axios 401 interceptor. This keeps auth state synchronized with
  // the local storage without a hard page navigation.
  useEffect(() => {
    const onSessionExpiry = () => logout()
    window.addEventListener('veyra-session-expired', onSessionExpiry)
    return () => window.removeEventListener('veyra-session-expired', onSessionExpiry)
  }, [logout])

  const applySession = useCallback((newToken, newUser) => {
    storeSession(newToken, newUser)
    setToken(newToken)
    setUser(newUser)
  }, [])

  const login = useCallback(
    async (email, password, expectedRole) => {
      const data = await authApi.login(email, password)
      const profile = {
        email: data.email,
        role: data.role,
        patientId: data.patientId ?? null,
        doctorId: data.doctorId ?? null,
        // Names arrive via /me endpoints; fill after hydration if available.
        firstName: data.firstName ?? null,
        lastName: data.lastName ?? null,
      }
      // Strict portal-role matching. The backend login response is the source
      // of truth; a mismatched login is rejected WITHOUT persisting a session.
      if (expectedRole && expectedRole !== profile.role) {
        const err = new Error('Portal role mismatch')
        err.code = 'PORTAL_ROLE_MISMATCH'
        throw err
      }
      applySession(data.token, profile)
      return profile
    },
    [applySession]
  )

  const register = useCallback(
    async (payload) => {
      const data = await authApi.register(payload) // always PATIENT on this endpoint
      const profile = {
        email: data.email,
        role: data.role,
        patientId: data.patientId ?? null,
        doctorId: null,
        firstName: payload.firstName,
        lastName: payload.lastName,
      }
      applySession(data.token, profile)
      return profile
    },
    [applySession]
  )

  /** Best-effort profile refresh after reload (patient/doctor have /me). */
  const revalidate = useCallback(async () => {
    if (!getStoredToken()) return
    try {
      if (role === 'PATIENT') {
        const me = await authApi.patientMe()
        setUser((u) => ({ ...u, ...me }))
      } else if (role === 'DOCTOR') {
        const me = await authApi.doctorMe()
        setUser((u) => ({ ...u, ...me }))
      }
      // ADMIN has no /me endpoint — persisted profile is sufficient.
    } catch {
      /* keep persisted profile; 401 is handled globally in the axios layer */
    }
  }, [role])

  const value = useMemo(
    () => ({
      user,
      token,
      role,
      isAuthenticated: Boolean(token),
      isAdmin: role === 'ADMIN',
      isPatient: role === 'PATIENT',
      isDoctor: role === 'DOCTOR',
      login,
      register,
      logout,
      revalidate,
    }),
    [user, token, role, login, register, logout, revalidate]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
