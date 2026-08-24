import { useEffect, useState } from 'react'
import Button from '../Button'
import { Textarea } from '../Input'
import { Skeleton } from '../LoadingSpinner'
import { ConfirmDialog } from '../Modal'
import { createConsultation, getConsultation } from '../../api/consultation'
import { toApiError } from '../../api/client'
import { formatSlotDateTime } from '../booking/dates'
import ConsultationRecord from './ConsultationRecord'
import PrescriptionEditor from './PrescriptionEditor'
import { validateDraft, draftIsValid, draftIsDirty } from './validation'

/**
 * Doctor consultation workspace for one appointment.
 *
 * - No consultation yet  -> "Start consultation" -> edit -> review -> complete
 * - Consultation exists  -> read-only record (backend exposes no update endpoint)
 *
 * Submission calls POST /api/appointments/{id}/consultation through
 * api/consultation.js. AI generation is intentionally NOT part of this flow —
 * the backend publishes its AFTER_COMMIT event on its own.
 */
export default function ConsultationWorkspace({
  appointmentId,
  appointmentStatus,
  consultation,
  consultationLoading = false,
  onCompleted,
}) {
  const [mode, setMode] = useState('start') // 'start' | 'edit' | 'review'
  const [clinicalNotes, setClinicalNotes] = useState('')
  const [followUpInstructions, setFollowUpInstructions] = useState('')
  const [prescriptions, setPrescriptions] = useState([])
  const [attempted, setAttempted] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState(null)
  const [confirmDiscard, setConfirmDiscard] = useState(false)

  const draft = { clinicalNotes, followUpInstructions, prescriptions }
  const dirty = draftIsDirty(draft)
  const errors = validateDraft(draft)
  const valid = draftIsValid(errors)
  const cancelled = appointmentStatus === 'CANCELLED'

  // Warn before closing the tab while a draft is in progress.
  useEffect(() => {
    if (!dirty || mode === 'start') return undefined
    const handler = (e) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [dirty, mode])

  function resetDraft() {
    setClinicalNotes('')
    setFollowUpInstructions('')
    setPrescriptions([])
    setAttempted(false)
    setSubmitError(null)
    setMode('start')
  }

  function handleStart() {
    setSubmitError(null)
    setMode('edit')
  }

  function handleReview(e) {
    e.preventDefault()
    setAttempted(true)
    if (!valid) return
    setSubmitError(null)
    setMode('review')
  }

  function handleCancel() {
    if (submitting) return
    if (dirty) {
      setConfirmDiscard(true)
    } else {
      resetDraft()
    }
  }

  async function handleComplete() {
    if (submitting) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const payload = {
        clinicalNotes: clinicalNotes.trim(),
        followUpInstructions: followUpInstructions.trim() || null,
        prescriptions: prescriptions.map((p) => ({
          medicationName: p.medicationName.trim(),
          dosage: p.dosage.trim(),
          frequency: p.frequency.trim(),
          durationDays: String(p.durationDays).trim() === '' ? null : Number(p.durationDays),
          instructions: p.instructions.trim() || null,
        })),
      }
      const created = await createConsultation(appointmentId, payload)
      resetDraft()
      onCompleted(created)
    } catch (err) {
      const apiErr = toApiError(err)
      if (apiErr.status === 403) {
        setSubmitError("You don't have permission to complete this consultation.")
      } else if (apiErr.status === 404) {
        setSubmitError('Appointment not found.')
      } else if (!err.response) {
        setSubmitError('Unable to reach the server. Please try again.')
      } else if (apiErr.status >= 500) {
        setSubmitError('Something went wrong while completing the consultation.')
      } else {
        // 400/409 etc. — surface the backend message cleanly. A duplicate
        // consultation (backend returns 400) resyncs to the saved record.
        setSubmitError(apiErr.message || 'Something went wrong while completing the consultation.')
        try {
          const existing = await getConsultation(appointmentId)
          resetDraft()
          onCompleted(existing)
        } catch {
          // keep the original error visible
        }
      }
    } finally {
      setSubmitting(false)
    }
  }

  const modeTag =
    mode === 'review' ? (
      <span className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 ring-1 ring-inset ring-amber-200">
        Review
      </span>
    ) : mode === 'edit' ? (
      <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
        Draft
      </span>
    ) : null

  return (
    <section className="card-base p-5" aria-label="Consultation workspace">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-semibold text-slate-900">Consultation</h2>
          {modeTag}
        </div>
        {consultation && (
          <span className="text-xs text-slate-400">
            Completed {formatSlotDateTime(consultation.consultationTimestamp)}
          </span>
        )}
      </div>

      {consultationLoading && <Skeleton className="h-32" />}

      {/* A. Consultation already exists — read-only, no edit controls
          (the backend exposes no consultation update endpoint). */}
      {!consultationLoading && consultation && (
        <ConsultationRecord
          clinicalNotes={consultation.clinicalNotes}
          followUpInstructions={consultation.followUpInstructions}
          prescriptions={consultation.prescriptions}
        />
      )}

      {/* B. No consultation yet. */}
      {!consultationLoading && !consultation && (
        <>
          {cancelled ? (
            <p className="text-sm text-slate-600">
              This appointment was cancelled, so a new consultation cannot be started from here.
            </p>
          ) : mode === 'start' ? (
            <div>
              <p className="text-sm text-slate-600">
                No consultation has been recorded for this appointment yet. Start the
                consultation to record clinical notes, prescriptions and follow-up instructions.
              </p>
              <Button className="mt-4" onClick={handleStart}>
                Start consultation
              </Button>
            </div>
          ) : mode === 'edit' ? (
            <form onSubmit={handleReview} noValidate className="space-y-5">
              <Textarea
                label="Clinical notes"
                rows={8}
                value={clinicalNotes}
                onChange={(e) => setClinicalNotes(e.target.value)}
                error={attempted ? errors.clinicalNotes : null}
                disabled={submitting}
                placeholder="Your clinical assessment and notes from this visit"
              />

              <Textarea
                label="Follow-up instructions"
                rows={4}
                value={followUpInstructions}
                onChange={(e) => setFollowUpInstructions(e.target.value)}
                disabled={submitting}
                placeholder="Optional — anything the patient should do after this visit"
              />

              <PrescriptionEditor
                items={prescriptions}
                onChange={setPrescriptions}
                rowErrors={errors.prescriptions}
                showErrors={attempted}
                disabled={submitting}
              />

              {attempted && !valid && (
                <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-medium text-red-700">
                  Please fix the highlighted fields before reviewing.
                </p>
              )}
              {submitError && (
                <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-medium text-red-700">
                  {submitError}
                </p>
              )}

              <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-4 sm:flex-row sm:justify-end">
                <Button variant="secondary" onClick={handleCancel} disabled={submitting}>
                  Cancel
                </Button>
                <Button type="submit">Review consultation</Button>
              </div>
            </form>
          ) : (
            <div className="space-y-5">
              <ConsultationRecord
                clinicalNotes={clinicalNotes}
                followUpInstructions={followUpInstructions.trim() || null}
                prescriptions={prescriptions}
              />

              {submitError && (
                <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-medium text-red-700">
                  {submitError}
                </p>
              )}

              <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-4 sm:flex-row sm:justify-end">
                <Button
                  variant="secondary"
                  onClick={() => setMode('edit')}
                  disabled={submitting}
                >
                  Back to edit
                </Button>
                <Button loading={submitting} onClick={handleComplete}>
                  {submitting ? 'Completing…' : 'Complete consultation'}
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <ConfirmDialog
        open={confirmDiscard}
        onClose={() => setConfirmDiscard(false)}
        onConfirm={() => {
          setConfirmDiscard(false)
          resetDraft()
        }}
        title="Discard consultation draft?"
        message="The clinical notes, prescriptions and follow-up instructions you entered will be cleared. This cannot be undone."
        confirmLabel="Discard draft"
        danger
      />
    </section>
  )
}
