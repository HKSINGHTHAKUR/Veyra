import client from './client'

/** GET /api/calendar/connect — returns { authorizationUrl } (single-use state inside) */
export function connectCalendar() {
  return client.get('/calendar/connect').then((r) => r.data)
}

/**
 * The Google redirect lands on /api/calendar/callback (public, handled by the
 * backend) which 302s the browser here. This endpoint is a frontend route only.
 */

/** GET /api/calendar/status -> { connected, userId } */
export function calendarStatus() {
  return client.get('/calendar/status').then((r) => r.data)
}

/** POST /api/calendar/disconnect */
export function disconnectCalendar() {
  return client.post('/calendar/disconnect').then((r) => r.data)
}
