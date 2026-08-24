import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import Button from '../../components/Button'
import Input from '../../components/Input'
import LoadingSpinner from '../../components/LoadingSpinner'
import EmptyState from '../../components/EmptyState'
import ErrorState from '../../components/ErrorState'
import { ConfirmDialog } from '../../components/Modal'
import { listLeaves, createLeave, deleteLeave } from '../../api/admin'

export default function AdminLeavesPage() {
  const { doctorId } = useParams()
  const [rows, setRows] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [leaveDate, setLeaveDate] = useState('')
  const [reason, setReason] = useState('')
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setRows(await listLeaves(doctorId))
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
    if (!leaveDate) return setFormError('Choose a leave date.')
    setSaving(true)
    try {
      await createLeave(doctorId, { leaveDate, reason: reason.trim() || null })
      setLeaveDate(''); setReason('')
      await load()
    } catch (e2) {
      setFormError(e2?.response?.data?.message || 'Could not add leave.')
    } finally {
      setSaving(false)
    }
  }

  async function onDelete(row) {
    setDeletingId(row.id)
    try {
      await deleteLeave(doctorId, row.id)
      setConfirmDelete(null)
      await load()
    } catch (e) {
      setError(e)
      setConfirmDelete(null)
    } finally {
      setDeletingId(null)
    }
  }

  const today = new Date().toISOString().slice(0, 10)
  const sorted = [...(rows || [])].sort((a, b) => (a.leaveDate < b.leaveDate ? 1 : -1))

  return (
    <>
      <PageHeader
        title="Doctor leave"
        subtitle="Patients with appointments on a leave date are notified automatically."
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
          <div>
            {(sorted.length) === 0 ? (
              <EmptyState
                icon="🌴"
                title="No leave recorded"
                description="Add leave dates so patients are informed and availability is cleared."
              />
            ) : (
              <ul className="space-y-3">
                {sorted.map((r) => (
                  <li key={r.id} className="card-base flex items-center justify-between gap-4 p-4">
                    <div>
                      <p className="text-sm font-semibold text-slate-900">{r.leaveDate}</p>
                      <p className="mt-0.5 text-sm text-slate-500">{r.reason || 'No reason given'}</p>
                    </div>
                    <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(r)}>
                      Remove
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <form onSubmit={onAdd} className="card-base h-fit space-y-4 p-5" noValidate>
            <h2 className="text-sm font-semibold text-slate-900">Add leave</h2>
            {formError && (
              <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-600/20">
                {formError}
              </p>
            )}
            <Input label="Leave date" type="date" min={today} value={leaveDate}
                   onChange={(e) => setLeaveDate(e.target.value)} />
            <Input label="Reason" value={reason} onChange={(e) => setReason(e.target.value)}
                   placeholder="Optional — shown to staff only" />
            <Button type="submit" loading={saving} className="w-full">Add leave</Button>
          </form>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(confirmDelete)}
        onClose={() => setConfirmDelete(null)}
        onConfirm={() => onDelete(confirmDelete)}
        title="Remove this leave?"
        message={`Availability on ${confirmDelete?.leaveDate ?? ''} will be offered to patients again.`}
        confirmLabel="Remove"
        loading={Boolean(deletingId)}
      />
    </>
  )
}
