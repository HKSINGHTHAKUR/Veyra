import { useCallback, useEffect, useRef, useState } from 'react'
import Modal from '../Modal'
import Button from '../Button'
import EmptyState from '../EmptyState'
import ErrorState from '../ErrorState'
import { Skeleton } from '../LoadingSpinner'
import DateSelector from '../booking/DateSelector'
import SlotGrid from '../booking/SlotGrid'
import { toApiError } from '../../api/client'
import { getAvailability } from '../../api/doctors'
import { rescheduleAppointment } from '../../api/appointments'
import {
  formatSlotDateTime,
  formatSlotTime,
  toLocalDateValue,
} from '../booking/dates'

/**
 * Reschedule dialog: date → availability → slot → confirm, reusing the
 * booking-flow DateSelector/SlotGrid. Availability comes straight from the
 * backend (which already excludes this appointment's own slot where relevant);
 * the selected UTC slotStart is sent back unchanged.
 */
export default function RescheduleDialog({ open, onClose, appointment, onRescheduled }) {
  const [date, setDate] = useState(() => toLocalDateValue(new Date()))
  const [availability, setAvailability] = useState(null)
  const [availLoading, setAvailLoading] = useState(false)
  const [availError, setAvailError] = useState(null)
  const [selectedSlot, setSelectedSlot] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState(null)
  const requestSeq = useRef(0)

  const loadAvailability = useCallback(
    async (d) => {
      const seq = ++requestSeq.current
      setAvailLoading(true)
      setAvailError(null)
      try {
        const data = await getAvailability(appointment.doctorId, d)
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
    [appointment.doctorId]
  )

  useEffect(() => {
    if (!open) return
    setSelectedSlot(null)
    setSubmitError(null)
  }, [open])

  useEffect(() => {
    if (!open) return
    loadAvailability(date)
  }, [open, date, loadAvailability])

  function onDateChange(d) {
    setDate(d)
    setSelectedSlot(null)
    setSubmitError(null)
  }

  async function onSubmit() {
    if (!selectedSlot || submitting) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const updated = await rescheduleAppointment(appointment.id, selectedSlot.slotStart)
      onRescheduled(updated)
    } catch (e) {
      const { status } = toApiError(e)
      if (status === 409) {
        setSelectedSlot(null)
        setSubmitError('The selected time is no longer available. Please choose another slot.')
        loadAvailability(date)
      } else if (!e.response) {
        setSubmitError('Unable to reach the server. Check your connection and try again.')
      } else {
        setSubmitError(toApiError(e).message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  const close = submitting ? () => {} : onClose
  const slots = availability?.slots ?? []
  const availableCount = slots.filter((s) => s.available).length

  return (
    <Modal
      open={open}
      onClose={close}
      title="Reschedule appointment"
      width="max-w-2xl"
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={submitting}>
            Keep current time
          </Button>
          <Button onClick={onSubmit} disabled={!selectedSlot} loading={submitting}>
            Confirm reschedule
          </Button>
        </>
      }
    >
      <div className="max-h-[65vh] overflow-y-auto pr-1">
        <p className="mb-4 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
          Currently:{' '}
          <span className="font-medium text-slate-900">
            {formatSlotDateTime(appointment.appointmentStart)}
          </span>
        </p>

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
                onSelect={(slot) => {
                  setSelectedSlot(slot)
                  setSubmitError(null)
                }}
                disabled={submitting}
              />
              <p className="mt-2 text-xs text-slate-400">
                Times shown in your local timezone. Greyed-out slots are unavailable.
              </p>
            </>
          )}
        </div>

        {selectedSlot && (
          <p className="mt-4 rounded-lg border border-primary-100 bg-primary-50/60 px-4 py-3 text-sm text-slate-700">
            New time:{' '}
            <span className="font-semibold text-slate-900">
              {formatSlotDateTime(selectedSlot.slotStart)}
            </span>{' '}
            <span className="text-slate-400">({formatSlotTime(selectedSlot.slotStart)} local)</span>
          </p>
        )}

        {submitError && (
          <p
            role="alert"
            className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {submitError}
          </p>
        )}
      </div>
    </Modal>
  )
}
