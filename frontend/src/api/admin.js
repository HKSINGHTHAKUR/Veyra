import client from './client'

// ── Doctors (admin) ──────────────────────────────────────────────────────────

/** GET /api/admin/doctors — full DoctorResponse list */
export function adminListDoctors() {
  return client.get('/admin/doctors').then((r) => r.data)
}

/** GET /api/admin/doctors/{id} */
export function adminGetDoctor(id) {
  return client.get(`/admin/doctors/${id}`).then((r) => r.data)
}

/** POST /api/admin/doctors */
export function adminCreateDoctor(payload) {
  return client.post('/admin/doctors', payload).then((r) => r.data)
}

/** PATCH /api/admin/doctors/{id} — partial update */
export function adminUpdateDoctor(id, patch) {
  return client.patch(`/admin/doctors/${id}`, patch).then((r) => r.data)
}

/** PATCH /api/admin/doctors/{id}/deactivate | /activate */
export function adminDeactivateDoctor(id) {
  return client.patch(`/admin/doctors/${id}/deactivate`).then((r) => r.data)
}
export function adminActivateDoctor(id) {
  return client.patch(`/admin/doctors/${id}/activate`).then((r) => r.data)
}

// ── Working hours ────────────────────────────────────────────────────────────

/** GET /api/admin/doctors/{id}/working-hours */
export function listWorkingHours(doctorId) {
  return client.get(`/admin/doctors/${doctorId}/working-hours`).then((r) => r.data)
}

/** POST /api/admin/doctors/{id}/working-hours */
export function createWorkingHours(doctorId, payload) {
  return client
    .post(`/admin/doctors/${doctorId}/working-hours`, payload)
    .then((r) => r.data)
}

/** PUT /api/admin/doctors/{id}/working-hours/{whId} */
export function updateWorkingHours(doctorId, whId, payload) {
  return client
    .put(`/admin/doctors/${doctorId}/working-hours/${whId}`, payload)
    .then((r) => r.data)
}

/** DELETE /api/admin/doctors/{id}/working-hours/{whId} */
export function deleteWorkingHours(doctorId, whId) {
  return client.delete(`/admin/doctors/${doctorId}/working-hours/${whId}`)
}

// ── Leaves ───────────────────────────────────────────────────────────────────

/** GET /api/admin/doctors/{id}/leaves */
export function listLeaves(doctorId) {
  return client.get(`/admin/doctors/${doctorId}/leaves`).then((r) => r.data)
}

/** POST /api/admin/doctors/{id}/leaves — { leaveDate, reason? } */
export function createLeave(doctorId, payload) {
  return client.post(`/admin/doctors/${doctorId}/leaves`, payload).then((r) => r.data)
}

/** DELETE /api/admin/doctors/{id}/leaves/{leaveId} */
export function deleteLeave(doctorId, leaveId) {
  return client.delete(`/admin/doctors/${doctorId}/leaves/${leaveId}`)
}
