import { NavLink } from 'react-router-dom'
import { cn } from '../lib/cn'
import { useAuth } from '../context/AuthContext'
import VeyraLogo from './VeyraLogo'

const icons = {
  home: (
    <path d="M2.25 12l8.954-8.955a1.126 1.126 0 011.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75" />
  ),
  doctors: (
    <path d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
  ),
  calendar: (
    <path d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5" />
  ),
  bell: (
    <path d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0" />
  ),
  stethoscope: (
    <path d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 2.05 1.36 3.79 3.24 4.393v2.357c0 3.314 2.686 6 6 6s6-2.686 6-6v-2.357C20.14 12.04 21 10.27 21 8.25z" />
  ),
  users: (
    <path d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
  ),
  clock: (
    <path d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
  ),
  plug: (
    <path d="M12 21a9 9 0 119-9m-9 9a9 9 0 019-9m-9 9V12m9-0a9 9 0 00-9 9m9-9H3" />
  ),
}

function NavIcon({ name }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-[18px] w-[18px] shrink-0"
      aria-hidden="true"
    >
      {icons[name]}
    </svg>
  )
}

const NAV = {
  PATIENT: [
    { to: '/patient', label: 'Overview', icon: 'home', end: true },
    { to: '/patient/doctors', label: 'Find a doctor', icon: 'doctors' },
    { to: '/patient/appointments', label: 'My appointments', icon: 'calendar' },
    { to: '/patient/notifications', label: 'Notifications', icon: 'bell' },
    { to: '/patient/calendar', label: 'Google Calendar', icon: 'plug' },
  ],
  DOCTOR: [
    { to: '/doctor', label: 'Overview', icon: 'home', end: true },
    { to: '/doctor/appointments', label: 'Appointments', icon: 'calendar' },
    { to: '/doctor/notifications', label: 'Notifications', icon: 'bell' },
    { to: '/doctor/calendar', label: 'Google Calendar', icon: 'plug' },
  ],
  ADMIN: [
    { to: '/admin', label: 'Overview', icon: 'home', end: true },
    { to: '/admin/doctors', label: 'Doctors', icon: 'stethoscope' },
  ],
}

export default function Sidebar({ open, onClose }) {
  const { role, user, logout } = useAuth()
  const items = NAV[role] || []

  return (
    <>
      {/* Mobile overlay */}
      <div
        className={cn(
          'fixed inset-0 z-30 bg-slate-900/40 lg:hidden',
          open ? 'block' : 'hidden'
        )}
        onClick={onClose}
        aria-hidden="true"
      />

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-slate-200 bg-white transition-transform duration-200 lg:static lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
        aria-label="Primary navigation"
      >
        {/* Brand */}
        <div className="flex h-16 items-center gap-2.5 border-b border-slate-100 px-5">
          <VeyraLogo className="h-8 w-auto" />
          <div className="leading-tight">
            <p className="text-sm font-semibold text-slate-900">Veyra</p>
            <p className="text-2xs text-slate-400">Appointment Manager</p>
          </div>
        </div>

        {/* Nav */}
        <nav className="slim-scrollbar flex-1 space-y-1 overflow-y-auto p-3">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={onClose}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-primary-50 text-primary-700'
                    : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                )
              }
            >
              <NavIcon name={item.icon} />
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* User footer */}
        <div className="border-t border-slate-100 p-3">
          <div className="mb-2 flex items-center gap-3 rounded-lg bg-slate-50 px-3 py-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-100 text-xs font-bold text-primary-700">
              {(user?.email || '?')[0].toUpperCase()}
            </div>
            <div className="min-w-0 leading-tight">
              <p className="truncate text-xs font-medium text-slate-800">{user?.email}</p>
              <p className="text-2xs capitalize text-slate-400">{role?.toLowerCase()}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={logout}
            className="w-full rounded-lg px-3 py-2 text-left text-sm font-medium text-slate-500 hover:bg-slate-50 hover:text-slate-700"
          >
            Sign out
          </button>
        </div>
      </aside>
    </>
  )
}
