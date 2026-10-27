const STEPS = [
  {
    number: '01',
    title: 'Ask Your Question',
    body: 'Describe your academic details, family income, category, or simply ask about any scholarship scheme in your own words.',
  },
  {
    number: '02',
    title: 'AI Analysis',
    body: 'Our assistant searches through official guideline documents and analyzes your profile against the eligibility criteria for all 5 schemes.',
  },
  {
    number: '03',
    title: 'Get Clear Answer',
    body: 'Receive a precise eligibility verdict with detailed reasoning, conditions, and references to official sources.',
  },
]

export function HowItWorks() {
  return (
    <section className="how-section" id="how">
      <div className="container">
        <div className="section-header">
          <div className="section-badge">Process</div>
          <h2 className="section-title">How It Works</h2>
          <p className="section-description">
            Get scholarship eligibility answers in three simple steps
          </p>
        </div>
        <div className="steps-grid">
          {STEPS.map((step) => (
            <article className="step-card" key={step.number}>
              <div className="step-number">{step.number}</div>
              <h3 className="step-title">{step.title}</h3>
              <p className="step-description">{step.body}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
