import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../Button'
import { Textarea } from '../Input'
import { Skeleton } from '../LoadingSpinner'
import EmptyState from '../EmptyState'
import ErrorState from '../ErrorState'
import StatusBadge from '../StatusBadge'
import { ConfirmDialog } from '../Modal'
import { toApiError } from '../../api/client'
import { getAvailability } from '../../api/doctors'
import {
  confirmAppointment,
  createHold,
  submitSymptoms,
} from '../../api/appointments'
import DateSelector from './DateSelector'
import SlotGrid from './SlotGrid'
import {
  formatRemaining,
  formatSlotDate,
  formatSlotDateTime,
  formatSlotTime,
  toLocalDateValue,
} from './dates'

const SYMPTOMS_MAX = 5000
const STEP_TITLES = {
  slot: 'Choose a date and time',
  symptoms: 'Tell the doctor what you\u2019d like help with',
  review: 'Review your appointment',
  confirmed: 'Appointment confirmed',
}

function InlineAlert({ children }) {
  return (
    <div role="alert" className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
      {children}
    </div>
  )
}

/**
 * Patient booking flow: availability → slot selection → hold (with live
 * countdown) → symptoms → review → confirmation. All business rules are
 * enforced by the backend; this component only mirrors backend state.
 */
export default function BookingPanel({ doctor }) {
  const [step, setStep] = useState('slot')
  const [date, setDate] = useState(() => toLocalDateValue(new Date()))
  const [availability, setAvailability] = useState(null)
  const [availLoading, setAvailLoading] = useState(false)
  const [availError, setAvailError] = useState(null)
  const [selectedSlot, setSelectedSlot] = useState(null)
  const [hold, setHold] = useState(null)
  const [holding, setHolding] = useState(false)
  const [holdError, setHoldError] = useState(null)
  const [symptomText, setSymptomText] = useState('')
  const [symptomError, setSymptomError] = useState(null)
  const [submittingSymptoms, setSubmittingSymptoms] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [confirmError, setConfirmError] = useState(null)
  const [appointment, setAppointment] = useState(null)
  const [abandonAsk, setAbandonAsk] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const requestSeq = useRef(0)

  // seq guard prevents a slow response for an old date overwriting a newer one
  const loadAvailability = useCallback(
    async (d) => {
      const seq = ++requestSeq.current
      setAvailLoading(true)
      setAvailError(null)
      try {
        const data = await getAvailability(doctor.id, d)
        if (seq === requestSeq.current) setAvailability(data)
      } catch (e) {
        if (seq === requestSeq.current) {
          setAvailability(null)
          setAvailError(e)
        }
      } finally {
        if (seq === requestSeq.current) setAvailLoading(false)
      }
    },
    [doctor.id]
  )

  useEffect(() => {
    loadAvailability(date)
  }, [loadAvailability, date])

  // Hold countdown: remaining time is always derived from the server's
  // expiresAt rather than decrementing a local counter.
  const holdActive = Boolean(hold) && step !== 'confirmed'
  useEffect(() => {
    if (!holdActive) return undefined
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [holdActive])

  // Warn before closing the tab while a slot is being held.
  useEffect(() => {
    if (!holdActive) return undefined
    const handler = (e) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [holdActive])

  const remainingMs = hold ? new Date(hold.expiresAt).getTime() - now : 0
  const holdExpired = Boolean(hold) && step !== 'confirmed' && remainingMs <= 0

  function onDateChange(d) {
    setDate(d)
    setSelectedSlot(null)
    setHoldError(null)
  }

  function onSlotSelect(slot) {
    setSelectedSlot(slot)
    setHoldError(null)
  }

  function resetToSlots(message = null) {
    setHold(null)
    setSelectedSlot(null)
    setStep('slot')
    setConfirmError(null)
    setHoldError(message)
    loadAvailability(date)
  }

  async function onHold() {
    if (!selectedSlot || holding) return
    setHolding(true)
    setHoldError(null)
    try {
      const h = await createHold(doctor.id, selectedSlot.slotStart)
      setHold(h)
      setNow(Date.now())
      setSymptomText('')
      setSymptomError(null)
      setStep('symptoms')
    } catch (e) {
      const { status } = toApiError(e)
      if (status === 409) {
        setSelectedSlot(null)
        setHoldError('The slot was just taken by another patient. Please choose another time.')
        loadAvailability(date)
      } else if (!e.response) {
        setHoldError('Unable to reach the server. Check your connection and try again.')
      } else {
        setHoldError(toApiError(e).message)
      }
    } finally {
      setHolding(false)
    }
  }

  async function onSubmitSymptoms(e) {
    e.preventDefault()
    if (holdExpired || submittingSymptoms) return
    const text = symptomText.trim()
    if (!text) {
      setSymptomError('Please describe what you would like help with.')
      return
    }
    if (text.length > SYMPTOMS_MAX) {
      setSymptomError(`Please keep it under ${SYMPTOMS_MAX} characters.`)
      return
    }
    setSubmittingSymptoms(true)
    setSymptomError(null)
    try {
      await submitSymptoms(hold.holdId, text)
      setStep('review')
    } catch (err) {
      const { status } = toApiError(err)
      if (status === 409) {
        resetToSlots('Your hold is no longer active. Please choose a slot again.')
      } else if (!err.response) {
        setSymptomError('Unable to reach the server. Check your connection and try again.')
      } else {
        setSymptomError(toApiError(err).message)
      }
    } finally {
      setSubmittingSymptoms(false)
    }
  }

  async function onConfirm() {
    if (holdExpired || confirming) return
    setConfirming(true)
    setConfirmError(null)
    try {
      const a = await confirmAppointment(hold.holdId)
      setAppointment(a)
      setStep('confirmed')
    } catch (err) {
      const { status } = toApiError(err)
      if (status === 409) {
        resetToSlots('That slot is no longer available. Please choose another time.')
      } else if (!err.response) {
        setConfirmError('Unable to reach the server. Check your connection and try again.')
      } else {
        setConfirmError(toApiError(err).message)
      }
    } finally {
      setConfirming(false)
    }
  }

  function abandonHold() {
    // No release endpoint exists by design — the abandoned hold simply
    // expires server-side once its TTL passes.
    setAbandonAsk(false)
    setSymptomText('')
    resetToSlots()
  }

  const slots = availability?.slots ?? []
  const availableCount = slots.filter((s) => s.available).length

  const holdPill = hold && step !== 'confirmed' && (
    <span
      className={
        holdExpired
          ? 'inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600'
          : 'inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700 ring-1 ring-inset ring-amber-600/25'
      }
      role="status"
      aria-live="off"
    >
      <span aria-hidden="true">⏱</span>
      {holdExpired ? 'Hold expired' : `Slot held for ${formatRemaining(remainingMs)}`}
    </span>
  )

  return (
    <div className="card-base p-5">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-900">{STEP_TITLES[step]}</h2>
        {holdPill}
      </div>

      {step === 'slot' && (
        <>
          <DateSelector value={date} onChange={onDateChange} />

          <div className="mt-5">
            {availLoading && (
              <div className="space-y-2">
                <Skeleton className="h-9 w-full" />
                <Skeleton className="h-9 w-full" />
              </div>
            )}
            {!availLoading && availError && (
              <ErrorState error={availError} onRetry={() => loadAvailability(date)} />
            )}
            {!availLoading && !availError && slots.length === 0 && (
              <EmptyState
                icon="📅"
                title="No appointments are available on this date"
                description="This doctor has no open slots on the selected day. Try another date."
              />
            )}
            {!availLoading && !availError && slots.length > 0 && availableCount === 0 && (
              <EmptyState
                icon="🕑"
                title="All slots are taken on this date"
                description="Every slot shown is already booked or held. Try another date."
              />
            )}
            {!availLoading && !availError && availableCount > 0 && (
              <>
                <SlotGrid
                  slots={slots}
                  selectedSlotStart={selectedSlot?.slotStart}
                  onSelect={onSlotSelect}
                  disabled={holding}
                />
                <p className="mt-2 text-xs text-slate-400">
                  Times shown in your local timezone. Greyed-out slots are unavailable.
                </p>
              </>
            )}
          </div>

          {selectedSlot && (
            <div className="mt-5 rounded-lg border border-primary-100 bg-primary-50/60 p-4">
              <p className="text-sm font-semibold text-slate-900">
                {formatSlotDate(selectedSlot.slotStart)} · {formatSlotTime(selectedSlot.slotStart)}
              </p>
              <p className="mt-0.5 text-sm text-slate-600">
                Dr. {doctor.firstName} {doctor.lastName} · {doctor.specialisation}
              </p>
              <Button className="mt-3 w-full sm:w-auto" onClick={onHold} loading={holding}>
                Hold this slot
              </Button>
            </div>
          )}

          {holdError && <InlineAlert>{holdError}</InlineAlert>}
        </>
      )}

      {step !== 'slot' && step !== 'confirmed' && holdExpired && (
        <EmptyState
          icon="⏱"
          title="Your slot hold has expired"
          description="The held slot was released. Choose another time to continue booking."
          action={
            <Button variant="secondary" onClick={() => resetToSlots()}>
              Choose another slot
            </Button>
          }
        />
      )}

      {step === 'symptoms' && !holdExpired && (
        <form onSubmit={onSubmitSymptoms} noValidate>
          <p className="mb-3 text-sm text-slate-600">
            {formatSlotDateTime(hold.slotStart)} · Dr. {doctor.firstName} {doctor.lastName}
          </p>
          <Textarea
            label="Symptoms or reason for visit"
            placeholder="e.g. recurring headaches for the past week, worse in the evenings"
            rows={5}
            value={symptomText}
            onChange={(e) => setSymptomText(e.target.value)}
            error={symptomError}
            hint="This is shared with your doctor before the visit. It is not a diagnosis."
            maxLength={SYMPTOMS_MAX + 200}
          />
          <p className="mt-1 text-right text-xs text-slate-400">
            {symptomText.length}/{SYMPTOMS_MAX}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Button type="submit" loading={submittingSymptoms}>
              Continue to review
            </Button>
            <Button variant="ghost" onClick={() => setAbandonAsk(true)}>
              Change slot
            </Button>
          </div>
        </form>
      )}

      {step === 'review' && !holdExpired && (
        <div>
          <dl className="divide-y divide-slate-100 rounded-lg border border-slate-200 text-sm">
            <div className="flex justify-between gap-4 px-4 py-2.5">
              <dt className="text-slate-500">Doctor</dt>
              <dd className="font-medium text-slate-900">
                Dr. {doctor.firstName} {doctor.lastName}
              </dd>
            </div>
            <div className="flex justify-between gap-4 px-4 py-2.5">
              <dt className="text-slate-500">Specialisation</dt>
              <dd className="font-medium text-slate-900">{doctor.specialisation}</dd>
            </div>
            <div className="flex justify-between gap-4 px-4 py-2.5">
              <dt className="text-slate-500">Date &amp; time</dt>
              <dd className="font-medium text-slate-900">{formatSlotDateTime(hold.slotStart)}</dd>
            </div>
          </dl>
          <div className="mt-3 rounded-lg bg-slate-50 px-4 py-3 text-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Symptoms shared</p>
            <p className="mt-1 whitespace-pre-wrap text-slate-700">{symptomText.trim()}</p>
          </div>
          {confirmError && <InlineAlert>{confirmError}</InlineAlert>}
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <Button onClick={onConfirm} loading={confirming}>
              Confirm appointment
            </Button>
            <Button variant="ghost" disabled={confirming} onClick={() => setStep('symptoms')}>
              Back
            </Button>
            <Button variant="ghost" disabled={confirming} onClick={() => setAbandonAsk(true)}>
              Change slot
            </Button>
          </div>
        </div>
      )}

      {step === 'confirmed' && appointment && (
        <div className="flex flex-col items-center py-4 text-center">
          <div
            aria-hidden="true"
            className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-xl"
          >
            ✅
          </div>
          <h3 className="text-base font-semibold text-slate-900">Appointment confirmed</h3>
          <p className="mt-1 text-sm text-slate-500">
            {formatSlotDateTime(appointment.appointmentStart)} · Dr. {doctor.firstName}{' '}
            {doctor.lastName} · {doctor.specialisation}
          </p>
          <div className="mt-2">
            <StatusBadge status={appointment.status} />
          </div>
          <p className="mt-3 max-w-sm text-xs text-slate-400">
            A confirmation has been recorded. You can view or manage this appointment from your
            appointments page.
          </p>
          <div className="mt-5 flex flex-wrap justify-center gap-2">
            <Link to={`/patient/appointments/${appointment.id}`}>
              <Button>View appointment</Button>
            </Link>
            <Link to="/patient/doctors">
              <Button variant="secondary">Back to doctors</Button>
            </Link>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={abandonAsk}
        onClose={() => setAbandonAsk(false)}
        onConfirm={abandonHold}
        title="Change slot?"
        message="Your slot is currently held. Leaving this step will abandon the hold — it will expire on its own and the slot may be released to other patients."
        confirmLabel="Abandon hold"
      />
    </div>
  )
}
