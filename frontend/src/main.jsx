import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './context/AuthContext'
import { API_BASE_URL } from './api/client'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>
)

// Warm the backend once on app load (e.g. Render cold start). Fire-and-forget:
// non-blocking, exactly one request, silent on failure, no JWT/credentials.
fetch(`${API_BASE_URL}/actuator/health`, {
  credentials: 'omit',
  cache: 'no-store',
}).catch(() => {})
