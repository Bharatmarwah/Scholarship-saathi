import { ArrowIcon, BookIcon, CheckIcon, ShieldIcon, SparkIcon } from './Icons'

interface HeroProps {
  onStart: () => void
  onExplore: () => void
}

export function Hero({ onStart, onExplore }: HeroProps) {
  return (
    <section className="hero" id="top">
      <div className="container">
        <div className="hero-grid">
          <div className="hero-content">
            <div className="hero-badge">
              <SparkIcon size={15} />
              <span>AI-Powered Scholarship Assistant</span>
            </div>
            <h1 className="hero-title">
              Find Scholarships You May Be <span className="hero-title-accent">Eligible</span> For
            </h1>
            <p className="hero-description">
              Get instant, guideline-based eligibility checks for 5 Central Government scholarship schemes.
              Ask in plain language and receive accurate answers with clear verdicts.
            </p>
            <div className="hero-actions">
              <button type="button" className="btn btn-primary" onClick={onStart}>
                Start AI Assistant
                <ArrowIcon size={17} />
              </button>
              <button type="button" className="btn btn-outline" onClick={onExplore}>
                View All Schemes
              </button>
            </div>
            <div className="hero-features">
              <div className="hero-feature">
                <ShieldIcon size={17} />
                <span>Official Guidelines</span>
              </div>
              <div className="hero-feature">
                <BookIcon size={17} />
                <span>Source Referenced</span>
              </div>
              <div className="hero-feature">
                <CheckIcon size={17} />
                <span>Instant Verdict</span>
              </div>
            </div>
          </div>
          <div className="hero-card-wrapper">
            <div className="hero-info-card">
              <div className="hero-info-header">
                <div className="hero-info-icon">
                  <ShieldIcon size={18} />
                </div>
                <div>
                  <h3>Eligibility Check</h3>
                  <p>PM-USP CSSS</p>
                </div>
                <span className="hero-info-badge">ELIGIBLE</span>
              </div>
              <div className="hero-info-body">
                <div className="hero-info-item">
                  <CheckIcon size={15} />
                  <span>Undergraduate - First Year</span>
                </div>
                <div className="hero-info-item">
                  <CheckIcon size={15} />
                  <span>Family Income: ₹3,00,000</span>
                </div>
                <div className="hero-info-item">
                  <CheckIcon size={15} />
                  <span>Regular Mode</span>
                </div>
                <div className="hero-info-item">
                  <CheckIcon size={15} />
                  <span>Meets Income Criteria</span>
                </div>
              </div>
              <div className="hero-info-footer">
                <span>Based on official scheme guidelines</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
