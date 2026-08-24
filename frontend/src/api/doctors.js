import client from './client'

/** GET /api/doctors?specialisation= — active doctors, patient-visible summary */
export function searchDoctors(specialisation) {
  const params = specialisation ? { specialisation } : {}
  return client.get('/doctors', { params }).then((r) => r.data)
}

/** GET /api/doctors/{doctorId} */
export function getDoctor(doctorId) {
  return client.get(`/doctors/${doctorId}`).then((r) => r.data)
}

/** GET /api/doctors/{doctorId}/availability?date=YYYY-MM-DD */
export function getAvailability(doctorId, date) {
  return client
    .get(`/doctors/${doctorId}/availability`, { params: { date } })
    .then((r) => r.data)
}
