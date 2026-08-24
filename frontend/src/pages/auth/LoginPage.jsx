import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import Input from '../../components/Input'
import Button from '../../components/Button'
import VeyraLogo from '../../components/VeyraLogo'
import { cn } from '../../lib/cn'
import { toApiError } from '../../api/client'

const ROLE_HOME = { PATIENT: '/patient', DOCTOR: '/doctor', ADMIN: '/admin' }

const PORTALS = [
  {
    id: 'PATIENT',
    title: 'Patient Portal',
    description: 'For patients booking and managing appointments',
    icon: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"
      />
    ),
  },
  {
    id: 'DOCTOR',
    title: 'Doctor Portal',
    description: 'For doctors managing appointments and consultations',
    icon: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 2.05 1.36 3.79 3.24 4.393v2.357c0 3.314 2.686 6 6 6s6-2.686 6-6v-2.357C20.14 12.04 21 10.27 21 8.25z"
      />
    ),
  },
  {
    id: 'ADMIN',
    title: 'Admin Portal',
    description: 'For administrators managing doctors and operations',
    icon: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z"
      />
    ),
  },
]

export default function LoginPage() {
  const { isAuthenticated, role, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()

  const [portal, setPortal] = useState('PATIENT')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  const sessionExpired = params.get('session') === 'expired'
  const from = location.state?.from || null

  // An already-authenticated user never sees the login form — the JWT role is
  // authoritative, so we route them straight to their own portal.
  if (isAuthenticated) {
    return <Navigate to={ROLE_HOME[role] || '/patient'} replace />
  }

  async function onSubmit(e) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      // The selected portal is compared against the role returned by the
      // backend. A mismatch throws before any session is stored, so the user
      // stays on /login with a clear message.
      const profile = await login(email.trim(), password, portal)
      const home = ROLE_HOME[profile.role] || '/patient'
      // Preserve deep links only when they belong to the authenticated portal.
      const target = from && from.startsWith(home) ? from : home
      navigate(target, { replace: true })
    } catch (err) {
      if (err?.code === 'PORTAL_ROLE_MISMATCH') {
        setError('You do not have permission to access this portal.')
      } else {
        setError(toApiError(err).message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  const selected = PORTALS.find((p) => p.id === portal)

  return (
    <div className="flex min-h-screen">
      {/* Left brand panel — desktop only */}
      <div className="hidden w-[42%] flex-col justify-between bg-primary-800 p-10 lg:flex">
        <div className="flex items-center gap-2.5 text-white">
          <VeyraLogo className="h-9 w-auto" />
          <span className="font-semibold tracking-tight">Veyra</span>
        </div>
        <div>
          <h1 className="max-w-md text-2xl font-semibold leading-snug text-white">
            Appointments, follow-ups and clinical summaries in one calm workspace.
          </h1>
          <p className="mt-3 max-w-md text-sm leading-relaxed text-primary-100">
            Slot-safe booking with double-booking prevention, pre-visit AI summaries,
            and reliable notifications.
          </p>
        </div>
        <p className="text-xs text-primary-200">For authorised healthcare staff and patients.</p>
      </div>

      {/* Form panel */}
      <div className="flex flex-1 items-center justify-center bg-surface px-4 py-12 sm:px-8">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden">
            <div className="mb-4 flex items-center gap-2.5">
              <VeyraLogo className="h-8 w-auto" />
              <span className="font-semibold text-slate-900">Veyra</span>
            </div>
          </div>

          <h2 className="text-xl font-semibold tracking-tight text-slate-900">Choose your portal</h2>
          <p className="mt-1 text-sm text-slate-500">
            Pick the area that matches your role, then sign in with your account.
          </p>

          {sessionExpired && !error && (
            <p className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-xs font-medium text-amber-700 ring-1 ring-inset ring-amber-600/20">
              Your session expired. Please sign in again.
            </p>
          )}
          {error && (
            <p role="alert" className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-600/20">
              {error}
            </p>
          )}

          {/* Portal selection — enforced against the backend role on login. */}
          <div role="radiogroup" aria-label="Choose your portal" className="mt-6 space-y-3">
            {PORTALS.map((p) => {
              const active = portal === p.id
              return (
                <button
                  key={p.id}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  onClick={() => setPortal(p.id)}
                  className={cn(
                    'flex w-full items-start gap-3 rounded-card border p-4 text-left transition-colors duration-150',
                    active
                      ? 'border-primary-500 bg-primary-50 shadow-card ring-1 ring-primary-500/30'
                      : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50'
                  )}
                >
                  <span
                    aria-hidden="true"
                    className={cn(
                      'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg transition-colors',
                      active ? 'bg-primary-600 text-white' : 'bg-slate-100 text-slate-500'
                    )}
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.7} className="h-5 w-5">
                      {p.icon}
                    </svg>
                  </span>
                  <span className="min-w-0">
                    <span className="block text-sm font-semibold text-slate-900">{p.title}</span>
                    <span className="mt-0.5 block text-xs leading-relaxed text-slate-500">{p.description}</span>
                  </span>
                  <span
                    aria-hidden="true"
                    className={cn(
                      'ml-auto mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full border transition-colors',
                      active ? 'border-primary-600' : 'border-slate-300'
                    )}
                  >
                    {active && <span className="h-2 w-2 rounded-full bg-primary-600" />}
                  </span>
                </button>
              )
            })}
          </div>

          <form onSubmit={onSubmit} className="mt-6 space-y-4" noValidate>
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
            />
            <Input
              label="Password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
            <Button type="submit" size="lg" loading={submitting} className="w-full">
              Sign in to {selected.title}
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-slate-500">
            New patient?{' '}
            <Link to="/register" className="font-medium text-primary-700 hover:text-primary-800">
              Create an account
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
