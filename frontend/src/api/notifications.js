import client from './client'

/** GET /api/notifications?status=&type= — own notifications for the current user */
export function myNotifications({ status, type } = {}) {
  const params = {}
  if (status) params.status = status
  if (type) params.type = type
  return client.get('/notifications', { params }).then((r) => r.data)
}

/** GET /api/notifications/{id} */
export function getNotification(id) {
  return client.get(`/notifications/${id}`).then((r) => r.data)
}
