import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import Button from '../../components/Button'
import Input, { Select } from '../../components/Input'
import LoadingSpinner from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import { ConfirmDialog } from '../../components/Modal'
import {
  listWorkingHours,
  createWorkingHours,
  deleteWorkingHours,
} from '../../api/admin'

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const EMPTY_FORM = { dayOfWeek: '', startTime: '09:00', endTime: '17:00', slotDurationMinutes: 30 }

export default function AdminWorkingHoursPage() {
  const { doctorId } = useParams()
  const [rows, setRows] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [form, setForm] = useState(EMPTY_FORM)
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setRows(await listWorkingHours(doctorId))
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [doctorId])

  useEffect(() => {
    load()
  }, [load])

  async function onAdd(e) {
    e.preventDefault()
    setFormError(null)
    if (!form.dayOfWeek) return setFormError('Choose a day of week.')
    if (form.startTime >= form.endTime) return setFormError('Start time must be before end time.')

    setSaving(true)
    try {
      await createWorkingHours(doctorId, { ...form })
      setForm((f) => ({ ...f, dayOfWeek: '' }))
      await load()
    } catch (e2) {
      // 409 = duplicate day; 400 = validation
      setFormError(e2?.response?.data?.message || 'Could not save schedule.')
    } finally {
      setSaving(false)
    }
  }

  async function onDelete(row) {
    setDeletingId(row.id)
    try {
      await deleteWorkingHours(doctorId, row.id)
      setConfirmDelete(null)
      await load()
    } catch (e) {
      setError(e)
      setConfirmDelete(null)
    } finally {
      setDeletingId(null)
    }
  }

  const takenDays = new Set((rows || []).map((r) => r.dayOfWeek))

  return (
    <>
      <PageHeader
        title="Working hours"
        subtitle="One recurring schedule per weekday. Slot duration drives the booking grid."
        actions={
          <Link to={`/admin/doctors/${doctorId}`} className="text-sm font-medium text-primary-700 hover:text-primary-800">
            ← Back to doctor
          </Link>
        }
      />

      {loading && <LoadingSpinner />}
      {!loading && error && <ErrorState error={error} onRetry={load} />}

      {!loading && !error && (
        <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
          {/* Existing schedules */}
          <div>
            {(rows?.length ?? 0) === 0 ? (
              <EmptyState
                icon="🕒"
                title="No working hours configured"
                description="Add the doctor's recurring weekly schedule using the form. Patients will see availability generated from it."
              />
            ) : (
              <ul className="space-y-3">
                {rows.map((r) => (
                  <li key={r.id} className="card-base flex items-center justify-between gap-4 p-4">
                    <div>
                      <p className="text-sm font-semibold text-slate-900">
                        {r.dayOfWeek.charAt(0) + r.dayOfWeek.slice(1).toLowerCase()}
                        {!r.active && <span className="ml-2 text-xs text-slate-400">(inactive)</span>}
                      </p>
                      <p className="mt-0.5 text-sm text-slate-500">
                        {r.startTime} – {r.endTime}
                        <span className="mx-1.5 text-slate-300">·</span>
                        {r.slotDurationMinutes}-minute slots
                      </p>
                    </div>
                    <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(r)}>
                      Remove
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Add form */}
          <form onSubmit={onAdd} className="card-base h-fit space-y-4 p-5" noValidate>
            <h2 className="text-sm font-semibold text-slate-900">Add schedule</h2>
            {formError && (
              <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-600/20">
                {formError}
              </p>
            )}
            <Select
              label="Day of week"
              value={form.dayOfWeek}
              onChange={(e) => setForm((f) => ({ ...f, dayOfWeek: e.target.value }))}
              placeholder="Choose a day…"
              options={DAYS.filter((d) => !takenDays.has(d)).map((d) => ({
                value: d,
                label: d.charAt(0) + d.slice(1).toLowerCase(),
              }))}
            />
            <div className="grid grid-cols-2 gap-4">
              <Input label="Start time" type="time" value={form.startTime}
                     onChange={(e) => setForm((f) => ({ ...f, startTime: e.target.value }))} />
              <Input label="End time" type="time" value={form.endTime}
                     onChange={(e) => setForm((f) => ({ ...f, endTime: e.target.value }))} />
            </div>
            <Input
              label="Slot duration (minutes)"
              type="number" min={5} max={480} step={5}
              value={form.slotDurationMinutes}
              onChange={(e) => setForm((f) => ({ ...f, slotDurationMinutes: Number(e.target.value) }))}
              hint="Patients book in steps of this length."
            />
            <Button type="submit" loading={saving} className="w-full">Add schedule</Button>
          </form>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(confirmDelete)}
        onClose={() => setConfirmDelete(null)}
        onConfirm={() => onDelete(confirmDelete)}
        title="Remove this schedule?"
        message={`Weekly ${confirmDelete?.dayOfWeek?.toLowerCase() ?? ''} availability will no longer be offered to patients.`}
        confirmLabel="Remove"
        loading={Boolean(deletingId)}
      />
    </>
  )
}
