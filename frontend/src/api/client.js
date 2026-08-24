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
 * 401 handling: session is invalid/expired -> hard logout once, then send the
 * user to /login. A flag prevents redirect loops when /login itself 401s.
 *
 * 403 is deliberately NOT intercepted globally: pages decide how to present an
 * unauthorized state (the backend is the real security boundary).
 */
let handling401 = false
client.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status
    const url = error?.config?.url || ''
    const isAuthCall = url.includes('/auth/login') || url.includes('/auth/register')

    if (status === 401 && !isAuthCall && !handling401) {
      handling401 = true
      clearSession()
      window.location.assign('/login?session=expired')
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
