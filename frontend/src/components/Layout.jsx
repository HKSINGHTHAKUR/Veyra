import { useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'

/** Derives a human title from the current path for the top bar. */
function titleFor(pathname) {
  const map = [
    ['/patient/appointments', 'My appointments'],
    ['/patient/doctors', 'Find a doctor'],
    ['/patient/notifications', 'Notifications'],
    ['/patient/calendar', 'Google Calendar'],
    ['/patient', 'Patient portal'],
    ['/doctor/appointments', 'Appointments'],
    ['/doctor/notifications', 'Notifications'],
    ['/doctor/calendar', 'Google Calendar'],
    ['/doctor', 'Doctor portal'],
    ['/admin/doctors', 'Doctor management'],
    ['/admin', 'Administration'],
  ]
  const hit = map.find(([p]) => pathname.startsWith(p))
  return hit ? hit[1] : 'Healthcare Manager'
}

/**
 * Authenticated application shell: sidebar + topbar + routed content.
 * Sidebar collapses into an overlay drawer below the lg breakpoint.
 */
export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const location = useLocation()

  // Close the mobile drawer on navigation
  useEffect(() => setSidebarOpen(false), [location.pathname])

  return (
    <div className="flex min-h-screen">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onMenuClick={() => setSidebarOpen(true)} title={titleFor(location.pathname)} />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
