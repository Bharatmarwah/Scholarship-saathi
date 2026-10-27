import { SCHEMES } from '../data/schemes'
import { CapIcon } from './Icons'

export function Footer() {
  return (
    <footer className="footer">
      <div className="container">
        <div className="footer-grid">
          <div className="footer-brand">
            <span className="brand-mark brand-mark-light">
              <CapIcon size={20} />
            </span>
            <div>
              <p className="footer-name">Scholarship Sarthi</p>
              <p className="footer-note">
                An AI assistant that answers scholarship questions from official guidelines and
                checks eligibility against the published rules.
              </p>
            </div>
          </div>
          <div>
            <p className="footer-heading">Schemes Covered</p>
            <ul className="footer-list">
              {SCHEMES.map((scheme) => (
                <li key={scheme.id}>
                  <a href="#schemes">{scheme.shortName}</a>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <p className="footer-heading">Disclaimer</p>
            <p className="footer-text">
              This is a demonstration interface and not an official Government of India website.
              All eligibility results are based on publicly available scheme guidelines and are
              advisory in nature. Please verify details on the{' '}
              <a href="https://scholarships.gov.in/" target="_blank" rel="noopener noreferrer">
                National Scholarship Portal
              </a>{' '}
              before applying.
            </p>
          </div>
        </div>
        <div className="footer-bottom">
          <div className="footer-bottom-content">
            <span>© {new Date().getFullYear()} Scholarship Sarthi. All rights reserved.</span>
            <span>Inspired by UX4G Design System - Government of India</span>
          </div>
        </div>
      </div>
    </footer>
  )
}
