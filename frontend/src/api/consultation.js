import client from './client'

/** POST /api/appointments/{appointmentId}/consultation — doctor only */
export function createConsultation(appointmentId, payload) {
  return client
    .post(`/appointments/${appointmentId}/consultation`, payload)
    .then((r) => r.data)
}

/** GET /api/appointments/{appointmentId}/consultation — owner/doctor/admin */
export function getConsultation(appointmentId) {
  return client.get(`/appointments/${appointmentId}/consultation`).then((r) => r.data)
}
