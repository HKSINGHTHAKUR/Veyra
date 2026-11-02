import axios from 'axios'

/**
 * Single axios instance for the whole app.
 * - Dev: Vite proxies /api -> http://localhost:8080 (see vite.config.js)
 * - Prod: set VITE_API_BASE_URL, e.g. https://api.example.com/api
 */
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || '/api'

export const TOKEN_KEY = 'hc.token'
export const USER_KEY = 'hc.user'

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getStoredUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function storeSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

const client = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

// Attach JWT to every authenticated request
client.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/**
 * Triggered when a stored token has expired or is otherwise unparseable.
 * Clears the local session and broadcasts a custom event so the AuthContext
 * can log the user out cleanly. The interceptor itself performs no
 * navigation: it must not disturb React Router's history stack or a pending
 * Back/Forward traversal.
 */
export function handleSessionExpiry(expiredToken) {
  // Don't clear a newer session established after this 401 was observed.
  if (expiredToken !== undefined && expiredToken !== getStoredToken()) return
  clearSession()
  window.dispatchEvent(new CustomEvent('veyra-session-expired'))
}

/**
 * Client-side expiry check mirroring the one used during hydration.
 * 401s on a token that is still structurally valid (e.g. a transient
 * backend error) do not force a logout; an expired/invalid token does.
 */
function isExpiredToken(token) {
  try {
    const part = token.split('.')[1]
    if (!part) return true
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const payload = JSON.parse(atob(padded))
    return !payload || typeof payload.exp !== 'number' || payload.exp * 1000 <= Date.now()
  } catch {
    return true
  }
}

/**
 * 401 handling: the stored token is expired or unparseable -> clear it and
 * broadcast so the AuthContext logs the user out. No navigation happens here;
 * the AuthContext remains the single source of truth for auth state.
 *
 * 403 is deliberately NOT intercepted globally: pages decide how to present an
 * unauthorized state (the backend is the real security boundary).
 */
client.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status
    const url = error?.config?.url || ''
    const isAuthCall = url.includes('/auth/login') || url.includes('/auth/register')
    const token = getStoredToken()

    if (status === 401 && !isAuthCall && token && isExpiredToken(token)) {
      handleSessionExpiry(token)
    }
    return Promise.reject(error)
  }
)

/** Normalises axios errors into { status, message } for UI states. */
export function toApiError(error) {
  const status = error?.response?.status
  const data = error?.response?.data
  let message = data?.message || error?.message || 'Something went wrong'
  if (data?.fieldErrors && typeof data.fieldErrors === 'object') {
    const first = Object.values(data.fieldErrors)[0]
    if (first) message = first
  }
  if (status === 403) message = 'You do not have permission to do that.'
  return { status, message }
}

export default client
