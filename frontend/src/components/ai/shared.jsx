import StatusBadge from '../StatusBadge'

/** Small sparkle mark for AI-assisted sections — restrained, no chatbot styling. */
export function AiMark() {
  return (
    <span aria-hidden="true" className="text-primary-500">
      ✦
    </span>
  )
}

/** Tinted card shell shared by both AI sections. */
export function AiCard({ title, subtitle, right, children }) {
  return (
    <section className="card-base overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 bg-primary-50/40 px-5 py-3">
        <div className="flex items-center gap-2">
          <AiMark />
          <div>
            <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
            {subtitle && <p className="text-xs text-slate-500">{subtitle}</p>}
          </div>
        </div>
        {right}
      </div>
      <div className="px-5 py-4">{children}</div>
    </section>
  )
}

export function AiNote({ children }) {
  return <p className="mt-4 text-xs text-slate-400">{children}</p>
}

export function AiStatusMessage({ icon, children }) {
  return (
    <div className="flex items-start gap-3 rounded-lg bg-slate-50 px-4 py-3">
      <span aria-hidden="true" className="text-lg leading-none">
        {icon}
      </span>
      <div className="flex-1 text-sm text-slate-600">{children}</div>
    </div>
  )
}

export function AiError({ children }) {
  return (
    <p
      role="alert"
      className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
    >
      {children}
    </p>
  )
}

/** "Low/Medium/High urgency" — text label plus badge, never color alone. */
export function UrgencyDisplay({ urgency }) {
  const label = String(urgency).toLowerCase()
  return (
    <span className="inline-flex items-center gap-2">
      <StatusBadge status={urgency} />
      <span className="text-xs font-medium capitalize text-slate-600">{label} urgency</span>
    </span>
  )
}
