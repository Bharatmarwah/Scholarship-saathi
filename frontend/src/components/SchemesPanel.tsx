import { SCHEMES } from '../data/schemes'
import { ArrowIcon } from './Icons'

interface SchemesPanelProps {
  onPrefill: (text: string) => void
}

export function SchemesPanel({ onPrefill }: SchemesPanelProps) {
  return (
    <aside className="sidebar" id="schemes">
      <section className="sidebar-section">
        <div className="sidebar-header">
          <h2 className="sidebar-title">Scholarship Schemes</h2>
          <p className="sidebar-subtitle">5 Central Government Schemes</p>
        </div>
        <div className="schemes-list">
          {SCHEMES.map((scheme) => (
            <article className="scheme-card" key={scheme.id}>
              <div className="scheme-card-header">
                <h3 className="scheme-card-title">{scheme.shortName}</h3>
                <span className="scheme-card-badge">{scheme.stage}</span>
              </div>
              <p className="scheme-card-fullname">{scheme.fullName}</p>
              <p className="scheme-card-ministry">{scheme.ministry}</p>
              <div className="scheme-card-details">
                <div className="scheme-detail-item">
                  <span className="scheme-detail-label">Income Limit</span>
                  <span className="scheme-detail-value">{scheme.income}</span>
                </div>
              </div>
              <div className="scheme-tags">
                {scheme.tags.map((tag) => (
                  <span className="scheme-tag" key={tag}>
                    {tag}
                  </span>
                ))}
              </div>
              <button type="button" className="scheme-action-btn" onClick={() => onPrefill(scheme.prompt)}>
                Check Eligibility
                <ArrowIcon size={14} />
              </button>
            </article>
          ))}
        </div>
      </section>

      <section className="info-card">
        <h3 className="info-card-title">Smart Profile Memory</h3>
        <p className="info-card-text">
          Your details like course, year, income, category, and domicile are remembered throughout
          the conversation. No need to repeat them. Start a new chat to reset.
        </p>
      </section>
    </aside>
  )
}
