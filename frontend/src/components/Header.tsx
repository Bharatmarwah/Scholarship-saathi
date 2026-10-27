import type { BackendState } from '../lib/types'
import { CapIcon, MoonIcon, PlusIcon, SunIcon } from './Icons'

interface HeaderProps {
  theme: 'light' | 'dark'
  backendState: BackendState
  onToggleTheme: () => void
  onNewChat: () => void
}

const STATUS_LABEL: Record<BackendState, string> = {
  online: 'Online',
  connecting: 'Connecting',
  offline: 'Offline',
}

export function Header({ theme, backendState, onToggleTheme, onNewChat }: HeaderProps) {
  return (
    <>
      <div className="gov-topbar">
        <div className="container gov-topbar-inner">
          <div className="gov-topbar-left">
            <span className="gov-emblem" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="18" height="18">
                <circle cx="12" cy="12" r="11" fill="none" stroke="#fff" strokeWidth="1.5" />
                <circle cx="12" cy="12" r="3" fill="#fff" />
                <g stroke="#fff" strokeWidth="1.2">
                  <line x1="12" y1="1" x2="12" y2="4" />
                  <line x1="12" y1="20" x2="12" y2="23" />
                  <line x1="1" y1="12" x2="4" y2="12" />
                  <line x1="20" y1="12" x2="23" y2="12" />
                  <line x1="4.22" y1="4.22" x2="6.34" y2="6.34" />
                  <line x1="17.66" y1="17.66" x2="19.78" y2="19.78" />
                  <line x1="4.22" y1="19.78" x2="6.34" y2="17.66" />
                  <line x1="17.66" y1="6.34" x2="19.78" y2="4.22" />
                </g>
              </svg>
            </span>
            <span className="gov-text">भारत सरकार | Government of India</span>
            <span className="gov-sep">|</span>
            <span className="gov-text">शिक्षा मंत्रालय | Ministry of Education</span>
          </div>
          <div className="gov-topbar-right">
            <span className="gov-text">National Scholarship Portal</span>
          </div>
        </div>
      </div>
      <div className="tricolor-stripe" aria-hidden="true" />
      <header className="site-header">
        <div className="container site-header-inner">
          <a className="site-brand" href="#top">
            <span className="site-brand-mark">
              <CapIcon size={24} />
            </span>
            <div className="site-brand-text">
              <span className="site-brand-name">Scholarship Sarthi</span>
              <span className="site-brand-tagline">AI Assistant for Central Government Scholarships</span>
            </div>
          </a>
          <nav className="site-nav">
            <a className="site-nav-link" href="#assistant">AI Assistant</a>
            <a className="site-nav-link" href="#schemes">Scholarships</a>
            <a className="site-nav-link" href="#how">How it works</a>
          </nav>
          <div className="site-header-actions">
            <div className={`gov-status gov-status-${backendState}`}>
              <span className="gov-status-dot" />
              <span>{STATUS_LABEL[backendState]}</span>
            </div>
            <button type="button" className="icon-btn" onClick={onToggleTheme} aria-label="Toggle theme">
              {theme === 'dark' ? <SunIcon size={18} /> : <MoonIcon size={18} />}
            </button>
            <button type="button" className="primary-btn" onClick={onNewChat}>
              <PlusIcon size={16} />
              New Chat
            </button>
          </div>
        </div>
      </header>
    </>
  )
}
