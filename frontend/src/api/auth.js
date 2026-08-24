import client from './client'

/** POST /api/auth/register — public. Returns { token, email, role, patientId } */
export function register({ firstName, lastName, email, password, phone }) {
  return client
    .post('/auth/register', { firstName, lastName, email, password, phone: phone || null })
    .then((r) => r.data)
}

/** POST /api/auth/login — public. Returns { token, email, role, patientId?/doctorId? } */
export function login(email, password) {
  return client.post('/auth/login', { email, password }).then((r) => r.data)
}

/** GET /api/patient/me */
export function patientMe() {
  return client.get('/patient/me').then((r) => r.data)
}

/** GET /api/doctor/me */
export function doctorMe() {
  return client.get('/doctor/me').then((r) => r.data)
}
