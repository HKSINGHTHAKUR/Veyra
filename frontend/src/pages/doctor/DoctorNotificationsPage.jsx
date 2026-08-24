import NotificationsPage from '../patient/NotificationsPage'

/**
 * Same inbox, same endpoint — the backend scopes results to the caller.
 * Kept as a thin alias so each portal owns its route tree.
 */
export default function DoctorNotificationsPage() {
  return <NotificationsPage />
}
