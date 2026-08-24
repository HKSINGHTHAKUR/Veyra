import client from './client'

/** GET /api/appointments/{id}/ai/pre-visit — owner/doctor/admin */
export function getPreVisitSummary(appointmentId) {
  return client.get(`/appointments/${appointmentId}/ai/pre-visit`).then((r) => r.data)
}

/** GET /api/appointments/{id}/ai/post-visit */
export function getPostVisitSummary(appointmentId) {
  return client.get(`/appointments/${appointmentId}/ai/post-visit`).then((r) => r.data)
}

/** POST .../ai/pre-visit/generate — owning patient or admin (manual retry) */
export function generatePreVisit(appointmentId) {
  return client
    .post(`/appointments/${appointmentId}/ai/pre-visit/generate`)
    .then((r) => r.data)
}

/** POST .../ai/post-visit/generate — assigned doctor or admin */
export function generatePostVisit(appointmentId) {
  return client
    .post(`/appointments/${appointmentId}/ai/post-visit/generate`)
    .then((r) => r.data)
}
