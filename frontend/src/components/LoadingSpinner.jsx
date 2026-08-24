import { cn } from '../lib/cn'

export default function LoadingSpinner({ fullScreen = false, label = 'Loading…', className }) {
  const spinner = (
    <div className={cn('flex flex-col items-center gap-3', className)} role="status" aria-live="polite">
      <span className="h-8 w-8 animate-spin rounded-full border-2 border-primary-500 border-t-transparent" />
      <span className="text-sm text-slate-500">{label}</span>
    </div>
  )
  if (!fullScreen) return spinner
  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      {spinner}
    </div>
  )
}

/** Skeleton block for list/card placeholders while loading. */
export function Skeleton({ className }) {
  return <div className={cn('animate-pulse rounded-lg bg-slate-200/70', className)} />
}
