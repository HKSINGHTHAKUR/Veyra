import { isRouteErrorResponse, useRouteError } from 'react-router-dom'

/** Router-level error boundary: render/Loader failures never blank-screen the app. */
export default function RouteErrorBoundary() {
  const error = useRouteError()
  const message = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : 'An unexpected error occurred while loading this page.'
  return (
    <div className="flex min-h-screen items-center justify-center p-6">
      <div className="card-base max-w-md p-8 text-center">
        <h1 className="text-base font-semibold text-slate-900">Page error</h1>
        <p className="mt-2 text-sm text-slate-500">{message}</p>
      </div>
    </div>
  )
}
