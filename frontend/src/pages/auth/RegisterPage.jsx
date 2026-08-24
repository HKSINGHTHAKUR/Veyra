import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import Input from '../../components/Input'
import Button from '../../components/Button'
import VeyraLogo from '../../components/VeyraLogo'
import { toApiError } from '../../api/client'

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    password: '',
    confirm: '',
  })
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function validate() {
    const errs = {}
    if (!form.firstName.trim()) errs.firstName = 'First name is required'
    if (!form.lastName.trim()) errs.lastName = 'Last name is required'
    if (!/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'Enter a valid email address'
    if (form.password.length < 8) errs.password = 'Password must be at least 8 characters'
    if (form.password !== form.confirm) errs.confirm = 'Passwords do not match'
    return errs
  }

  async function onSubmit(e) {
    e.preventDefault()
    setError(null)
    const errs = validate()
    setFieldErrors(errs)
    if (Object.keys(errs).length > 0) return

    setSubmitting(true)
    try {
      await register({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        password: form.password,
      })
      navigate('/patient', { replace: true }) // public registration is PATIENT-only
    } catch (err) {
      const api = toApiError(err)
      setError(api.status === 409 ? 'An account with this email already exists.' : api.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface px-4 py-12 sm:px-8">
      <div className="w-full max-w-md">
        <div className="mb-6 flex items-center gap-2.5">
          <VeyraLogo className="h-8 w-auto" />
          <span className="font-semibold text-slate-900">Veyra</span>
        </div>

        <h1 className="text-xl font-semibold tracking-tight text-slate-900">Create your patient account</h1>
        <p className="mt-1 text-sm text-slate-500">
          Staff accounts are provisioned by the practice administrator.
        </p>

        {error && (
          <p role="alert" className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-600/20">
            {error}
          </p>
        )}

        <form onSubmit={onSubmit} className="card-base mt-6 space-y-4 p-5" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="First name" required value={form.firstName} onChange={set('firstName')} error={fieldErrors.firstName} autoComplete="given-name" />
            <Input label="Last name" required value={form.lastName} onChange={set('lastName')} error={fieldErrors.lastName} autoComplete="family-name" />
          </div>
          <Input label="Email" type="email" required value={form.email} onChange={set('email')} error={fieldErrors.email} autoComplete="email" placeholder="you@example.com" />
          <Input label="Phone" type="tel" value={form.phone} onChange={set('phone')} hint="Optional" autoComplete="tel" />
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Password" type="password" required value={form.password} onChange={set('password')} error={fieldErrors.password} autoComplete="new-password" hint="Minimum 8 characters" />
            <Input label="Confirm password" type="password" required value={form.confirm} onChange={set('confirm')} error={fieldErrors.confirm} autoComplete="new-password" />
          </div>
          <Button type="submit" size="lg" loading={submitting} className="w-full">
            Create account
          </Button>
        </form>

        <p className="mt-5 text-center text-sm text-slate-500">
          Already registered?{' '}
          <Link to="/login" className="font-medium text-primary-700 hover:text-primary-800">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
