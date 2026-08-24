import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../../components/PageHeader'
import Input from '../../components/Input'
import Button from '../../components/Button'
import { toApiError } from '../../api/client'
import { adminCreateDoctor } from '../../api/admin'

const EMPTY = {
  firstName: '', lastName: '', email: '',
  phone: '', specialisation: '', bio: '', consultationFee: '', temporaryPassword: '',
}

export default function AdminNewDoctorPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState(EMPTY)
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function validate() {
    const errs = {}
    if (!form.firstName.trim()) errs.firstName = 'Required'
    if (!form.lastName.trim()) errs.lastName = 'Required'
    if (!/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'Valid email required'
    if (!form.specialisation.trim()) errs.specialisation = 'Required'
    if (form.temporaryPassword.length < 8) errs.temporaryPassword = 'Minimum 8 characters'
    return errs
  }

  async function onSubmit(e) {
    e.preventDefault()
    setError(null)
    const errs = validate()
    setFieldErrors(errs)
    if (Object.keys(errs).length) return

    setSubmitting(true)
    try {
      const created = await adminCreateDoctor({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim() || null,
        specialisation: form.specialisation.trim(),
        bio: form.bio.trim() || null,
        consultationFee: form.consultationFee ? Number(form.consultationFee) : null,
        temporaryPassword: form.temporaryPassword,
      })
      // Temporary password is communicated out-of-band by the practice.
      navigate(`/admin/doctors/${created.id}`, { state: { justCreated: true } })
    } catch (err) {
      const api = toApiError(err)
      if (api.status === 409) setError('An account with this email already exists.')
      else setError(api.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Add doctor"
        subtitle="Creates a DOCTOR account. Share the temporary password with the doctor securely."
        actions={
          <Button variant="secondary" onClick={() => navigate('/admin/doctors')}>
            Cancel
          </Button>
        }
      />

      {error && (
        <p role="alert" className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-600/20">
          {error}
        </p>
      )}

      <form onSubmit={onSubmit} className="card-base max-w-2xl space-y-4 p-5" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="First name" required value={form.firstName} onChange={set('firstName')} error={fieldErrors.firstName} />
          <Input label="Last name" required value={form.lastName} onChange={set('lastName')} error={fieldErrors.lastName} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Email" type="email" required value={form.email} onChange={set('email')} error={fieldErrors.email} placeholder="doctor@practice.com" />
          <Input label="Phone" type="tel" value={form.phone} onChange={set('phone')} />
        </div>
        <Input label="Specialisation" required value={form.specialisation} onChange={set('specialisation')} error={fieldErrors.specialisation} placeholder="Cardiology" />
        <Input label="Bio" hint="Shown to patients on the doctor profile." value={form.bio} onChange={set('bio')} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Consultation fee" type="number" min="0" step="0.01" value={form.consultationFee} onChange={set('consultationFee')} hint="Optional, e.g. 120.00" />
          <Input label="Temporary password" type="text" required value={form.temporaryPassword} onChange={set('temporaryPassword')} error={fieldErrors.temporaryPassword} hint="Min 8 characters — share securely" autoComplete="off" />
        </div>
        <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
          <Button type="submit" loading={submitting}>Create doctor</Button>
        </div>
      </form>
    </>
  )
}
