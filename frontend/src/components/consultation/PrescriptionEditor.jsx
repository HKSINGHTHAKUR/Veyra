import Button from '../Button'
import Input, { Textarea } from '../Input'

/**
 * Repeatable prescription rows for the consultation workspace.
 * Fields exactly mirror CreateConsultationRequest.PrescriptionRequest:
 * medicationName (required), dosage (required), frequency (required),
 * durationDays (optional), instructions (optional).
 */
export default function PrescriptionEditor({
  items,
  onChange,
  rowErrors = [],
  showErrors = false,
  disabled = false,
}) {
  function update(key, field, value) {
    onChange(items.map((it) => (it.key === key ? { ...it, [field]: value } : it)))
  }

  function remove(key) {
    onChange(items.filter((it) => it.key !== key))
  }

  function add() {
    onChange([...items, { key: nextRowKey(), medicationName: '', dosage: '', frequency: '', durationDays: '', instructions: '' }])
  }

  return (
    <div>
      <div className="mb-2 flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-900">Prescriptions</h3>
        <Button type="button" variant="secondary" size="sm" onClick={add} disabled={disabled}>
          + Add medication
        </Button>
      </div>

      {items.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-200 px-4 py-3 text-sm text-slate-500">
          No medications yet. Add one if this visit needs a prescription — it is fine to
          complete the consultation without any.
        </p>
      ) : (
        <ul className="space-y-3">
          {items.map((row, index) => {
            const errors = showErrors ? rowErrors[index] || {} : {}
            return (
              <li
                key={row.key}
                aria-label={`Prescription ${index + 1}`}
                className="rounded-lg border border-slate-200 bg-slate-50/60 p-4"
              >
                <div className="mb-3 flex items-center justify-between gap-2">
                  <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Medication {index + 1}
                  </span>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => remove(row.key)}
                    disabled={disabled}
                  >
                    Remove
                  </Button>
                </div>

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
                  <Input
                    label="Medication"
                    value={row.medicationName}
                    onChange={(e) => update(row.key, 'medicationName', e.target.value)}
                    error={errors.medicationName}
                    maxLength={255}
                    disabled={disabled}
                    placeholder="e.g. Amoxicillin"
                  />
                  <Input
                    label="Dosage"
                    value={row.dosage}
                    onChange={(e) => update(row.key, 'dosage', e.target.value)}
                    error={errors.dosage}
                    maxLength={100}
                    disabled={disabled}
                    placeholder="e.g. 500 mg"
                  />
                  <Input
                    label="Frequency"
                    value={row.frequency}
                    onChange={(e) => update(row.key, 'frequency', e.target.value)}
                    error={errors.frequency}
                    maxLength={100}
                    disabled={disabled}
                    placeholder="e.g. 3 times a day"
                  />
                  <Input
                    label="Duration (days)"
                    type="number"
                    min={1}
                    step={1}
                    value={row.durationDays}
                    onChange={(e) => update(row.key, 'durationDays', e.target.value)}
                    error={errors.durationDays}
                    disabled={disabled}
                    placeholder="Optional"
                  />
                </div>

                <div className="mt-3">
                  <Textarea
                    label="Instructions"
                    rows={2}
                    value={row.instructions}
                    onChange={(e) => update(row.key, 'instructions', e.target.value)}
                    maxLength={1000}
                    disabled={disabled}
                    placeholder="Optional — how the patient should take this medication"
                  />
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

let rowCounter = 0
function nextRowKey() {
  rowCounter += 1
  return `rx-${rowCounter}`
}
