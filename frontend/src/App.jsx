import { useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import ProtectedRoute from './routes/ProtectedRoute'
import Layout from './components/Layout'
import RouteErrorBoundary from './components/RouteErrorBoundary'

import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import LandingPage from './pages/landing/LandingPage'

import PatientDashboard from './pages/patient/PatientDashboard'
import DoctorsPage from './pages/patient/DoctorsPage'
import DoctorDetailPage from './pages/patient/DoctorDetailPage'
import PatientAppointmentsPage from './pages/patient/PatientAppointmentsPage'
import PatientAppointmentDetailPage from './pages/patient/PatientAppointmentDetailPage'
import NotificationsPage from './pages/patient/NotificationsPage'
import CalendarPage from './pages/patient/CalendarPage'

import DoctorDashboard from './pages/doctor/DoctorDashboard'
import DoctorAppointmentsPage from './pages/doctor/DoctorAppointmentsPage'
import DoctorAppointmentDetailPage from './pages/doctor/DoctorAppointmentDetailPage'
import DoctorNotificationsPage from './pages/doctor/DoctorNotificationsPage'

import AdminDashboard from './pages/admin/AdminDashboard'
import AdminDoctorsPage from './pages/admin/AdminDoctorsPage'
import AdminNewDoctorPage from './pages/admin/AdminNewDoctorPage'
import AdminDoctorDetailPage from './pages/admin/AdminDoctorDetailPage'
import AdminWorkingHoursPage from './pages/admin/AdminWorkingHoursPage'
import AdminLeavesPage from './pages/admin/AdminLeavesPage'

import CalendarConnectedPage from './pages/calendar/CalendarConnectedPage'
import UnauthorizedPage from './pages/UnauthorizedPage'
import NotFoundPage from './pages/NotFoundPage'

const roleHome = (role) =>
  ({ PATIENT: '/patient', DOCTOR: '/doctor', ADMIN: '/admin' })[role] || '/login'

export default function App() {
  const { isAuthenticated, revalidate } = useAuth()

  // Opportunistic profile refresh after a page reload
  useEffect(() => {
    if (isAuthenticated) revalidate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  return (
    <Routes errorElement={<RouteErrorBoundary />}>
      {/* Public */}
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<Register />} />

      {/* Public OAuth landing page (backend already stored credentials) */}
      <Route element={isAuthenticated ? <Layout /> : <Navigate to="/login" replace />}>
        <Route path="/calendar/connected" element={<CalendarConnectedPage />} />
      </Route>

      {/* Patient portal */}
      <Route path="/patient" element={<ProtectedRoute allow={['PATIENT']}><Layout /></ProtectedRoute>}>
        <Route index element={<PatientDashboard />} />
        <Route path="doctors" element={<DoctorsPage />} />
        <Route path="doctors/:doctorId" element={<DoctorDetailPage />} />
        <Route path="appointments" element={<PatientAppointmentsPage />} />
        <Route path="appointments/:appointmentId" element={<PatientAppointmentDetailPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="calendar" element={<CalendarPage />} />
      </Route>

      {/* Doctor portal */}
      <Route path="/doctor" element={<ProtectedRoute allow={['DOCTOR']}><Layout /></ProtectedRoute>}>
        <Route index element={<DoctorDashboard />} />
        <Route path="appointments" element={<DoctorAppointmentsPage />} />
        <Route path="appointments/:appointmentId" element={<DoctorAppointmentDetailPage />} />
        <Route path="notifications" element={<DoctorNotificationsPage />} />
        <Route path="calendar" element={<CalendarPage />} />
      </Route>

      {/* Admin portal */}
      <Route path="/admin" element={<ProtectedRoute allow={['ADMIN']}><Layout /></ProtectedRoute>}>
        <Route index element={<AdminDashboard />} />
        <Route path="doctors" element={<AdminDoctorsPage />} />
        <Route path="doctors/new" element={<AdminNewDoctorPage />} />
        <Route path="doctors/:doctorId" element={<AdminDoctorDetailPage />} />
        <Route path="doctors/:doctorId/working-hours" element={<AdminWorkingHoursPage />} />
        <Route path="doctors/:doctorId/leaves" element={<AdminLeavesPage />} />
      </Route>

      <Route path="/403" element={<UnauthorizedPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

/** /register redirects patients who are already signed in. */
function Register() {
  const { isAuthenticated, role } = useAuth()
  if (isAuthenticated) return <Navigate to={roleHome(role)} replace />
  return <RegisterPage />
}
