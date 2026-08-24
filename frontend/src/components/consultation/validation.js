/**
 * Frontend mirror of the backend CreateConsultationRequest constraints.
 * The backend remains authoritative — these checks only catch obviously
 * invalid input before a round trip. No clinical/medical rules here.
 */

export function prescriptionRowErrors(row) {
  const errors = {}
  if (!row.medicationName.trim()) {
    errors.medicationName = 'Medication name is required.'
  }
  if (!row.dosage.trim()) {
    errors.dosage = 'Dosage is required.'
  }
  if (!row.frequency.trim()) {
    errors.frequency = 'Frequency is required.'
  }
  const raw = String(row.durationDays ?? '').trim()
  if (raw !== '') {
    const n = Number(raw)
    if (!Number.isInteger(n) || n < 1) {
      errors.durationDays = 'Duration must be a whole number of days (1 or more).'
    }
  }
  return errors
}

export function validateDraft({ clinicalNotes, prescriptions }) {
  return {
    clinicalNotes: clinicalNotes.trim() ? null : 'Clinical notes are required.',
    prescriptions: prescriptions.map(prescriptionRowErrors),
  }
}

export function draftIsValid(errors) {
  return !errors.clinicalNotes && errors.prescriptions.every((e) => Object.keys(e).length === 0)
}

/** True when the doctor has entered anything worth protecting from loss. */
export function draftIsDirty({ clinicalNotes, followUpInstructions, prescriptions }) {
  return (
    clinicalNotes.trim() !== '' ||
    followUpInstructions.trim() !== '' ||
    prescriptions.length > 0
  )
}

export function emptyPrescriptionRow(key) {
  return {
    key,
    medicationName: '',
    dosage: '',
    frequency: '',
    durationDays: '',
    instructions: '',
  }
}
