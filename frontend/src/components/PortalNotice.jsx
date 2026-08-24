import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

/**
 * Dismissible banner shown once after a login where the selected portal did
 * not match the authenticated role (e.g. picked "Doctor Portal" but the
 * account is a PATIENT). Carried via navigation state; never persisted.
 */
export default function PortalNotice() {
  const location = useLocation()
  const navigate = useNavigate()
  const [dismissed, setDismissed] = useState(false)

  const message = location.state?.portalNotice
  if (!message || dismissed) return null

  function dismiss() {
    setDismissed(true)
    navigate(location.pathname, { replace: true, state: null })
  }

  return (
    <div
      role="status"
      className="mb-6 flex items-start gap-3 rounded-lg bg-primary-50 px-4 py-3 text-sm text-primary-800 ring-1 ring-inset ring-primary-600/20"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.7}
        className="mt-0.5 h-4 w-4 shrink-0"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z"
        />
      </svg>
      <p className="flex-1 leading-relaxed">{message}</p>
      <button
        type="button"
        onClick={dismiss}
        aria-label="Dismiss"
        className="shrink-0 rounded-md p-1 text-primary-600 transition-colors hover:bg-primary-100 hover:text-primary-800"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} className="h-4 w-4" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  )
}
