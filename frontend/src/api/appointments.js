import client from './client'

/** GET /api/patient/appointments */
export function patientAppointments() {
  return client.get('/patient/appointments').then((r) => r.data)
}

/** GET /api/doctor/appointments */
export function doctorAppointments() {
  return client.get('/doctor/appointments').then((r) => r.data)
}

/** GET /api/appointments/{id} — owner patient / assigned doctor / admin */
export function getAppointment(appointmentId) {
  return client.get(`/appointments/${appointmentId}`).then((r) => r.data)
}

/** POST /api/appointments/hold — { doctorId, slotStart } -> HoldResponse */
export function createHold(doctorId, slotStart) {
  return client.post('/appointments/hold', { doctorId, slotStart }).then((r) => r.data)
}

/** POST /api/appointments/{holdId}/symptoms — { symptoms } */
export function submitSymptoms(holdId, symptoms) {
  return client.post(`/appointments/${holdId}/symptoms`, { symptoms }).then((r) => r.data)
}

/** POST /api/appointments/{holdId}/confirm -> AppointmentResponse */
export function confirmAppointment(holdId) {
  return client.post(`/appointments/${holdId}/confirm`).then((r) => r.data)
}

/** POST /api/appointments/{id}/cancel — { reason? } */
export function cancelAppointment(appointmentId, reason) {
  return client
    .post(`/appointments/${appointmentId}/cancel`, { reason: reason || null })
    .then((r) => r.data)
}

/** POST /api/appointments/{id}/reschedule — { newSlotStart } */
export function rescheduleAppointment(appointmentId, newSlotStart) {
  return client
    .post(`/appointments/${appointmentId}/reschedule`, { newSlotStart })
    .then((r) => r.data)
}
