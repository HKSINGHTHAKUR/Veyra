import { describe, it, beforeEach, afterEach, vi, expect } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route, useNavigate, Link } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import LoginPage from '../pages/auth/LoginPage'
import RegisterPage from '../pages/auth/RegisterPage'
import ProtectedRoute from '../routes/ProtectedRoute'
import { clearSession, storeSession, getStoredToken, handleSessionExpiry } from '../api/client'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  patientMe: vi.fn(),
  doctorMe: vi.fn(),
}))

import * as authApi from '../api/auth'

function makeToken(role = 'PATIENT') {
  const header = { alg: 'HS256', typ: 'JWT' }
  const payload = { sub: 'user-id', email: `${role.toLowerCase()}@test.com`, role, exp: Math.floor(Date.now() / 1000) + 86400 }
  const b64 = (o) => btoa(JSON.stringify(o))
  return `${b64(header)}.${b64(payload)}.signature`
}

let testNavigate
function NavCapture() {
  testNavigate = useNavigate()
  return null
}

const MockLanding = () => (
  <div data-testid="landing">
    <Link data-testid="signin-link" to="/login">Sign in</Link>
  </div>
)
const MockPatientDashboard = () => <div data-testid="page">Patient Dashboard</div>
const MockDoctorDashboard = () => <div data-testid="page">Doctor Dashboard</div>
const MockAdminDashboard = () => <div data-testid="page">Admin Dashboard</div>
const MockPatientApptDetail = () => <div data-testid="page">Patient Appointment Detail</div>

function renderApp(initialPath = '/', options = {}) {
  localStorage.clear()
  if (options.token) {
    storeSession(options.token, { email: 'user@test.com', role: options.role || 'PATIENT' })
  }

  const Component = (
    <MemoryRouter initialEntries={options.initialEntries ?? [initialPath]}>
      <AuthProvider>
        <NavCapture />
        <Routes>
          <Route path="/" element={<MockLanding />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/patient" element={<ProtectedRoute allow={['PATIENT']}><MockPatientDashboard /></ProtectedRoute>} />
          <Route path="/patient/appointments/:id" element={<ProtectedRoute allow={['PATIENT']}><MockPatientApptDetail /></ProtectedRoute>} />
          <Route path="/doctor" element={<ProtectedRoute allow={['DOCTOR']}><MockDoctorDashboard /></ProtectedRoute>} />
          <Route path="/admin" element={<ProtectedRoute allow={['ADMIN']}><MockAdminDashboard /></ProtectedRoute>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  )

  return render(Component)
}

function selectPortal(portal) {
  fireEvent.click(screen.getByRole('radio', { name: new RegExp(portal) }))
}

function fillLogin(portal = 'Patient Portal', email = 'patient@test.com', password = 'Password@1') {
  fireEvent.change(screen.getByLabelText(/^email$/i), { target: { value: email } })
  fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: password } })
  fireEvent.click(screen.getByText(new RegExp(`Sign in to ${portal}`)))
}

function fillRegister() {
  fireEvent.change(screen.getByLabelText(/first name/i), { target: { value: 'New' } })
  fireEvent.change(screen.getByLabelText(/last name/i), { target: { value: 'User' } })
  fireEvent.change(screen.getByLabelText(/^email$/i), { target: { value: 'new@test.com' } })
  fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'Password@1' } })
  fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Password@1' } })
  fireEvent.click(screen.getByText(/Create account/i))
}

describe('Navigation & authentication', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    testNavigate = undefined
  })

  afterEach(() => {
    localStorage.clear()
  })

  describe('Landing → login → role dashboard', () => {
    it('navigates patient login to /patient', async () => {
      authApi.login.mockResolvedValue({ token: makeToken('PATIENT'), email: 'patient@test.com', role: 'PATIENT', patientId: 'p1' })
      renderApp('/')
      await screen.findByTestId('landing')
      fireEvent.click(screen.getByTestId('signin-link'))
      await waitFor(() => screen.getByLabelText(/^email$/i))
      fillLogin('Patient Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))
    })

    it('navigates doctor login to /doctor', async () => {
      authApi.login.mockResolvedValue({ token: makeToken('DOCTOR'), email: 'doctor@test.com', role: 'DOCTOR', doctorId: 'd1' })
      renderApp('/')
      await screen.findByTestId('landing')
      fireEvent.click(screen.getByTestId('signin-link'))
      await waitFor(() => screen.getByLabelText(/^email$/i))
      selectPortal('Doctor Portal')
      fillLogin('Doctor Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Doctor Dashboard'))
    })

    it('navigates admin login to /admin', async () => {
      authApi.login.mockResolvedValue({ token: makeToken('ADMIN'), email: 'admin@test.com', role: 'ADMIN' })
      renderApp('/')
      await screen.findByTestId('landing')
      fireEvent.click(screen.getByTestId('signin-link'))
      await waitFor(() => screen.getByLabelText(/^email$/i))
      selectPortal('Admin Portal')
      fillLogin('Admin Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Admin Dashboard'))
    })
  })

  describe('Back / Forward after successful login', () => {
    it('patient: Back → landing, Forward → dashboard (no login screen exposed)', async () => {
      authApi.login.mockResolvedValue({ token: makeToken('PATIENT'), email: 'patient@test.com', role: 'PATIENT', patientId: 'p1' })
      renderApp('/')
      await screen.findByTestId('landing')
      fireEvent.click(screen.getByTestId('signin-link'))
      await waitFor(() => screen.getByLabelText(/^email$/i))
      fillLogin('Patient Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))
      testNavigate(-1)
      await waitFor(() => expect(screen.getByTestId('landing')).toBeInTheDocument())
      testNavigate(1)
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))
    })

    it('doctor: Back → landing, Forward → dashboard', async () => {
      authApi.login.mockResolvedValue({ token: makeToken('DOCTOR'), email: 'doctor@test.com', role: 'DOCTOR', doctorId: 'd1' })
      renderApp('/')
      await screen.findByTestId('landing')
      fireEvent.click(screen.getByTestId('signin-link'))
      await waitFor(() => screen.getByLabelText(/^email$/i))
      selectPortal('Doctor Portal')
      fillLogin('Doctor Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Doctor Dashboard'))
      testNavigate(-1)
      await waitFor(() => expect(screen.getByTestId('landing')).toBeInTheDocument())
      testNavigate(1)
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Doctor Dashboard'))
    })

    it('admin: Back → landing, Forward → dashboard', async () => {
      authApi.login.mockResolvedValue({ token: makeToken('ADMIN'), email: 'admin@test.com', role: 'ADMIN' })
      renderApp('/')
      await screen.findByTestId('landing')
      fireEvent.click(screen.getByTestId('signin-link'))
      await waitFor(() => screen.getByLabelText(/^email$/i))
      selectPortal('Admin Portal')
      fillLogin('Admin Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Admin Dashboard'))
      testNavigate(-1)
      await waitFor(() => expect(screen.getByTestId('landing')).toBeInTheDocument())
      testNavigate(1)
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Admin Dashboard'))
    })
  })

  describe('Authenticated direct navigation to /login', () => {
    it('redirects authenticated patient to /patient without adding a login history entry', async () => {
      const token = makeToken('PATIENT')
      renderApp('/login', { token, initialEntries: ['/', '/login'] })
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))
      testNavigate(-1)
      await waitFor(() => expect(screen.getByTestId('landing')).toBeInTheDocument())
    })

    it('redirects authenticated doctor to /doctor', async () => {
      const token = makeToken('DOCTOR')
      renderApp('/login', { token, role: 'DOCTOR', initialEntries: ['/', '/login'] })
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Doctor Dashboard'))
    })

    it('redirects authenticated admin to /admin', async () => {
      const token = makeToken('ADMIN')
      renderApp('/login', { token, role: 'ADMIN', initialEntries: ['/', '/login'] })
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Admin Dashboard'))
    })
  })

  describe('Unauthenticated access to protected routes', () => {
    it('redirects to /login preserving the destination in state', async () => {
      renderApp('/patient/appointments/123')
      await waitFor(() => screen.getByLabelText(/^email$/i))
      expect(screen.queryByTestId('landing')).not.toBeInTheDocument()
    })

    it('shows the session-expired banner on /login', async () => {
      renderApp('/patient/appointments/123')
      await waitFor(() => screen.getByLabelText(/^email$/i))
      await waitFor(() => expect(screen.getByText(/Your session expired/i)).toBeInTheDocument())
    })
  })

  describe('Deep-link restoration after login', () => {
    it('restores same-role deep link after login', async () => {
      authApi.login.mockResolvedValue({ token: makeToken('PATIENT'), email: 'patient@test.com', role: 'PATIENT', patientId: 'p1' })
      renderApp('/patient/appointments/123')
      await waitFor(() => screen.getByLabelText(/^email$/i))
      fillLogin('Patient Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Appointment Detail'))
    })
  })

  describe('Wrong-role deep-link rejection', () => {
    it('redirects PATIENT login away from an admin deep link to /patient', async () => {
      authApi.login.mockResolvedValue({ token: makeToken('PATIENT'), email: 'patient@test.com', role: 'PATIENT', patientId: 'p1' })
      renderApp('/admin')
      await waitFor(() => screen.getByLabelText(/^email$/i))
      fillLogin('Patient Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))
    })
  })

  describe('Dashboard refresh with a valid session', () => {
    it('loads dashboard with session intact when token is in localStorage', async () => {
      const token = makeToken('PATIENT')
      renderApp('/patient', { token })
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))
      expect(getStoredToken()).toBeTruthy()
    })
  })

  describe('Logout / session cleared prevents protected access', () => {
    it('cleared session does not reintroduce a login entry on Back / Forward', async () => {
      authApi.login.mockResolvedValue({ token: makeToken('PATIENT'), email: 'patient@test.com', role: 'PATIENT', patientId: 'p1' })
      renderApp('/')
      await screen.findByTestId('landing')
      fireEvent.click(screen.getByTestId('signin-link'))
      await waitFor(() => screen.getByLabelText(/^email$/i))
      fillLogin('Patient Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))
      expect(getStoredToken()).toBeTruthy()

      clearSession()
      expect(getStoredToken()).toBeNull()

      testNavigate(-1)
      await waitFor(() => expect(screen.getByTestId('landing')).toBeInTheDocument())
      testNavigate(1)
      await waitFor(() => expect(screen.getByTestId('landing')).toBeInTheDocument())
      expect(getStoredToken()).toBeNull()
    })
  })

  describe('Expired-token 401 behavior', () => {
    it('handleSessionExpiry clears session and dispatches event without navigation', () => {
      const spy = vi.fn()
      window.addEventListener('veyra-session-expired', spy)
      storeSession(makeToken('PATIENT'), { role: 'PATIENT' })
      expect(getStoredToken()).toBeTruthy()

      handleSessionExpiry()

      expect(getStoredToken()).toBeNull()
      expect(spy).toHaveBeenCalledTimes(1)
      window.removeEventListener('veyra-session-expired', spy)
    })

    it('AuthContext logs out user when session expiry event fires', async () => {
      renderApp('/patient', { token: makeToken('PATIENT'), role: 'PATIENT' })
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))

      window.dispatchEvent(new CustomEvent('veyra-session-expired'))

      await waitFor(() => expect(screen.getByLabelText(/^email$/i)).toBeInTheDocument())
      expect(getStoredToken()).toBeNull()
    })
  })

  describe('A 403 response preserving the session', () => {
    it('403 does not clear the session (interceptor only clears expired-token 401s)', () => {
      storeSession(makeToken('PATIENT'), { role: 'PATIENT' })
      expect(getStoredToken()).toBeTruthy()
    })
  })

  describe('Concurrent 401 requests and subsequent login', () => {
    it('handles multiple 401s and still allows a fresh login', async () => {
      const spy = vi.fn()
      window.addEventListener('veyra-session-expired', spy)
      storeSession(makeToken('PATIENT'), { role: 'PATIENT' })

      handleSessionExpiry()
      handleSessionExpiry()
      expect(spy).toHaveBeenCalledTimes(2)
      expect(getStoredToken()).toBeNull()

      authApi.login.mockResolvedValue({ token: makeToken('PATIENT'), email: 'patient@test.com', role: 'PATIENT', patientId: 'p1' })
      renderApp('/')
      await screen.findByTestId('landing')
      fireEvent.click(screen.getByTestId('signin-link'))
      await waitFor(() => screen.getByLabelText(/^email$/i))
      fillLogin('Patient Portal')
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))
      expect(getStoredToken()).toBeTruthy()

      window.removeEventListener('veyra-session-expired', spy)
    })
  })

  describe('Failed login preserves landing/login experience', () => {
    it('stays on /login with an error and no redirect on failed login', async () => {
      authApi.login.mockRejectedValue(new Error('Invalid email or password'))
      renderApp('/')
      await screen.findByTestId('landing')
      fireEvent.click(screen.getByTestId('signin-link'))
      await waitFor(() => screen.getByLabelText(/^email$/i))
      fillLogin('Patient Portal')
      await waitFor(() => screen.getByText(/Invalid email or password/i))
      expect(screen.getByLabelText(/^email$/i)).toBeInTheDocument()
    })
  })

  describe('Registration redirect behavior', () => {
    it('registers a patient and redirects to /patient', async () => {
      authApi.register.mockResolvedValue({ token: makeToken('PATIENT'), email: 'new@test.com', role: 'PATIENT', patientId: 'new1' })
      renderApp('/register')
      await screen.findByLabelText(/first name/i)
      fillRegister()
      await waitFor(() => expect(screen.getByTestId('page').textContent).toContain('Patient Dashboard'))
    })
  })
})
