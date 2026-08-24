import { useId } from 'react'
import { cn } from '../lib/cn'

export default function Input({
  label,
  error,
  hint,
  className,
  type = 'text',
  ...rest
}) {
  const autoId = useId()
  const id = rest.id || autoId
  const errorId = `${id}-error`
  const hintId = `${id}-hint`

  return (
    <div>
      {label && (
        <label htmlFor={id} className="label-base">
          {label}
        </label>
      )}
      <input
        id={id}
        type={type}
        aria-invalid={!!error}
        aria-describedby={error ? errorId : hint ? hintId : undefined}
        className={cn('input-base', error && 'input-error', className)}
        {...rest}
      />
      {hint && !error && (
        <p id={hintId} className="mt-1 text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="mt-1 text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}

export function Select({ label, error, options = [], placeholder, className, ...rest }) {
  const autoId = useId()
  const id = rest.id || autoId
  return (
    <div>
      {label && (
        <label htmlFor={id} className="label-base">
          {label}
        </label>
      )}
      <select
        id={id}
        aria-invalid={!!error}
        className={cn('input-base appearance-none pr-8', error && 'input-error', className)}
        {...rest}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options.map((o) =>
          typeof o === 'string' ? (
            <option key={o} value={o}>
              {o}
            </option>
          ) : (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          )
        )}
      </select>
      {error && (
        <p role="alert" className="mt-1 text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}

export function Textarea({ label, error, rows = 4, className, ...rest }) {
  const autoId = useId()
  const id = rest.id || autoId
  return (
    <div>
      {label && (
        <label htmlFor={id} className="label-base">
          {label}
        </label>
      )}
      <textarea
        id={id}
        rows={rows}
        aria-invalid={!!error}
        className={cn('input-base resize-y', error && 'input-error', className)}
        {...rest}
      />
      {error && (
        <p role="alert" className="mt-1 text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
