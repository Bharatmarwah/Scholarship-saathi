import { ArrowIcon, BookIcon, CheckIcon, ShieldIcon, SparkIcon } from '../components/Icons'
import { SUGGESTIONS } from '../data/schemes'

interface HomeProps {
  onStart: (query?: string) => void
}

export function Home({ onStart }: HomeProps) {
  return (
    <div className="home">
      <section className="hero">
        <div className="container">
          <div className="hero-inner">
            <div className="hero-badge">
              <SparkIcon size={16} />
              <span>Government of India • Ministry of Education</span>
            </div>
            <h1 className="hero-title">
              Scholarship Sarthi
              <span className="hero-title-sub">AI Assistant for Central Government Scholarships</span>
            </h1>
            <p className="hero-desc">
              Discover eligible Central Government scholarships, verify criteria, and get clear answers grounded in official guidelines.
            </p>
            <div className="prompt-wrap">
              <div className="prompt-bar">
                <input
                  type="text"
                  className="prompt-input"
                  placeholder="Ask about scholarships, eligibility, documents, or paste your details..."
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && e.currentTarget.value.trim()) {
                      onStart(e.currentTarget.value.trim())
                      e.currentTarget.value = ''
                    }
                  }}
                />
                <button
                  type="button"
                  className="prompt-btn"
                  onClick={(e) => {
                    const input = e.currentTarget.previousElementSibling as HTMLInputElement
                    if (input.value.trim()) {
                      onStart(input.value.trim())
                      input.value = ''
                    }
                  }}
                  aria-label="Send"
                >
                  <ArrowIcon size={18} />
                </button>
              </div>
              <div className="prompt-chips">
                {SUGGESTIONS.slice(0, 4).map((suggestion) => (
                  <button
                    type="button"
                    className="prompt-chip"
                    key={suggestion}
                    onClick={() => onStart(suggestion)}
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="features">
        <div className="container">
          <div className="section-head">
            <h2 className="section-title">Why use Scholarship Sarthi</h2>
            <p className="section-subtitle">Simple, accurate, and aligned with official scholarship criteria</p>
          </div>
          <div className="features-grid">
            <article className="feature-card">
              <div className="feature-icon">
                <ShieldIcon size={22} />
              </div>
              <h3>Eligibility Check</h3>
              <p>Get a clear eligibility assessment based on your category, course, domicile, income and more.</p>
            </article>
            <article className="feature-card">
              <div className="feature-icon">
                <BookIcon size={22} />
              </div>
              <h3>Official Guidelines</h3>
              <p>Responses are grounded in published Central Government scholarship scheme guidelines.</p>
            </article>
            <article className="feature-card">
              <div className="feature-icon">
                <CheckIcon size={22} />
              </div>
              <h3>Focused Schemes</h3>
              <p>Covers key Central schemes including PM-USP CSSS, Ishan Uday, PM YASASVI, Top Class Schemes.</p>
            </article>
          </div>
        </div>
      </section>
    </div>
  )
}
