/** Honest, non-technical message for a failed or unreachable AI request. */
export function aiActionMessage(error) {
  const status = error?.response?.status
  if (status === 403) return "You don't have permission to generate this summary."
  if (status === 404) return 'This appointment no longer exists.'
  if (!error?.response) return 'Unable to reach the server. Please try again.'
  if (status >= 500) return 'Something went wrong while preparing the summary.'
  return 'Something went wrong while preparing the summary.'
}
