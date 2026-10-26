import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

const root = document.getElementById('root')
if (!root) throw new Error('#root 요소를 찾지 못했어요')

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
