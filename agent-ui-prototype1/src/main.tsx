import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <div className="ring-ui-theme-dark" style={{ height: '100%' }}>
      <App />
    </div>
  </StrictMode>,
)
