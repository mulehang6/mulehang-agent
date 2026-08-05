import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import TimelineDesign from './TimelineDesign'
import './timeline.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <div className="ring-ui-theme-dark tl-root">
      <TimelineDesign />
    </div>
  </StrictMode>,
)
