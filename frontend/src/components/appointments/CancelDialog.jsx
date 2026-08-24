import { useEffect, useState } from 'react'
import Modal from '../Modal'
import Button from '../Button'
import { Textarea } from '../Input'

/**
 * Cancellation confirmation dialog. Reason is optional (CancelRequest.reason,
 * max 500 chars). The actual cancellation only happens after the user confirms
 * and the backend responds successfully.
 */
export default function CancelDialog({ open, onClose, onConfirm, loading, error }) {
  const [reason, setReason] = useState('')

  useEffect(() => {
    if (open) setReason('')
  }, [open])

  const close = loading ? () => {} : onClose

  return (
    <Modal
      open={open}
      onClose={close}
      title="Cancel this appointment?"
      width="max-w-md"
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={loading}>
            Keep appointment
          </Button>
          <Button variant="danger" loading={loading} onClick={() => onConfirm(reason.trim() || null)}>
            Cancel appointment
          </Button>
        </>
      }
    >
      <p className="text-sm leading-relaxed text-slate-600">
        The appointment will be cancelled and the slot released to other patients. Cancellation
        cannot be undone.
      </p>
      <div className="mt-3">
        <Textarea
          label="Reason (optional)"
          rows={3}
          maxLength={500}
          placeholder="Optional — shared with your doctor"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </div>
      {error && (
        <p
          role="alert"
          className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
        >
          {error}
        </p>
      )}
    </Modal>
  )
}
