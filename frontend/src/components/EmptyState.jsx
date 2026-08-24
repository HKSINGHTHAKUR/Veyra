export default function EmptyState({ icon = '🗂️', title, description, action }) {
  return (
    <div className="card-base flex flex-col items-center justify-center px-6 py-14 text-center">
      <div
        aria-hidden="true"
        className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-xl"
      >
        {icon}
      </div>
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}
