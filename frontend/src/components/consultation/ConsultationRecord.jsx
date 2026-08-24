/**
 * Read-only rendering of a consultation (used both for the saved record and
 * for the review step before submission). Only renders what was entered —
 * nothing is invented or auto-filled.
 */
export default function ConsultationRecord({ clinicalNotes, followUpInstructions, prescriptions }) {
  const items = prescriptions || []
  return (
    <div>
      <h3 className="text-xs font-medium uppercase tracking-wide text-slate-400">Clinical notes</h3>
      <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
        {clinicalNotes}
      </p>

      <h3 className="mt-4 text-xs font-medium uppercase tracking-wide text-slate-400">
        Prescriptions
      </h3>
      {items.length === 0 ? (
        <p className="mt-1 text-sm text-slate-500">No medications prescribed.</p>
      ) : (
        <ul className="mt-2 divide-y divide-slate-100 rounded-lg border border-slate-200">
          {items.map((p, index) => (
            <li key={p.id ?? p.key ?? index} className="px-3 py-2.5 text-sm">
              <span className="font-medium text-slate-900">{p.medicationName}</span>
              <span className="text-slate-500">
                {p.dosage ? ` — ${p.dosage}` : ''}
                {p.frequency ? `, ${p.frequency}` : ''}
                {hasDuration(p.durationDays) ? `, ${p.durationDays} days` : ''}
              </span>
              {p.instructions && (
                <p className="mt-0.5 text-xs text-slate-500">{p.instructions}</p>
              )}
            </li>
          ))}
        </ul>
      )}

      <h3 className="mt-4 text-xs font-medium uppercase tracking-wide text-slate-400">
        Follow-up instructions
      </h3>
      {followUpInstructions ? (
        <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
          {followUpInstructions}
        </p>
      ) : (
        <p className="mt-1 text-sm text-slate-500">None provided.</p>
      )}
    </div>
  )
}

function hasDuration(value) {
  return value !== null && value !== undefined && String(value).trim() !== ''
}
