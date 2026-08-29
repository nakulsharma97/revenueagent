export default function TransactionModal({ attempt, onClose }) {
  if (!attempt) return null;

  const tx = attempt.transaction;
  const sub = tx?.subscription;
  const customer = sub?.customer;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
      }}
      onClick={onClose}
    >
      <div
        className="panel"
        style={{
          width: 540,
          maxHeight: '80vh',
          overflow: 'auto',
          padding: 0,
          border: '2px solid var(--text)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header — receipt stub style */}
        <div style={{
          padding: '16px 20px',
          borderBottom: '2px solid var(--text)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <div>
            <div style={{
              fontFamily: 'var(--font-display)',
              fontSize: 9,
              fontWeight: 600,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
              color: 'var(--text-muted)',
              marginBottom: 4,
            }}>
              Case File — Transaction Detail
            </div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 22, fontWeight: 700, color: 'var(--text)' }}>
              TXN#{tx?.id}
            </div>
            {attempt.batchId && (
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--text-muted)', marginTop: 2 }}>
                BATCH: {attempt.batchId.slice(0, 12)}
              </div>
            )}
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'var(--rule)',
              borderRadius: 0,
              width: 28,
              height: 28,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--text-muted)',
              fontSize: 14,
              fontFamily: 'var(--font-mono)',
            }}
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div style={{ padding: '16px 20px' }}>
          {/* Action & Outcome — ruled row */}
          <div style={{ display: 'flex', borderBottom: 'var(--rule)', paddingBottom: 12, marginBottom: 12 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 9, fontWeight: 600, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 3 }}>Action</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--text)', fontSize: 14 }}>{attempt.actionTaken?.replaceAll('_', ' ')}</div>
            </div>
            <div style={{ flex: 1, borderLeft: 'var(--rule)', paddingLeft: 12 }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 9, fontWeight: 600, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 3 }}>Outcome</div>
              <div style={{
                fontFamily: 'var(--font-mono)',
                fontWeight: 700,
                fontSize: 14,
                color: attempt.outcome === 'SUCCESS' ? 'var(--ink-green)' : attempt.outcome === 'FAILED' ? 'var(--ink-red)' : 'var(--ink-amber)',
              }}>
                {attempt.outcome}
              </div>
            </div>
            <div style={{ flex: 1, borderLeft: 'var(--rule)', paddingLeft: 12 }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 9, fontWeight: 600, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 3 }}>LLM Driven</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 500, color: attempt.llmDriven ? 'var(--ink-purple)' : 'var(--text-muted)', fontSize: 14 }}>
                {attempt.llmDriven ? 'YES' : 'NO'}
              </div>
            </div>
          </div>

          {/* Signoff warning */}
          {attempt.requiresHumanSignoff && (
            <div style={{
              padding: '8px 12px',
              background: 'var(--red-bg)',
              border: '1px solid var(--ink-red)',
              marginBottom: 12,
              fontFamily: 'var(--font-mono)',
              fontSize: 12,
              color: 'var(--ink-red)',
              fontWeight: 600,
            }}>
              ⚠ REQUIRES HUMAN SIGNOFF: {attempt.signoffReason || 'No reason provided'}
            </div>
          )}

          {/* Reasoning — ruled section */}
          <div style={{ borderBottom: 'var(--rule)', paddingBottom: 12, marginBottom: 12 }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 9, fontWeight: 600, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>
              Agent Reasoning
            </div>
            <div style={{
              padding: '10px 12px',
              background: 'var(--surface-2)',
              fontFamily: 'var(--font-mono)',
              fontSize: 12,
              color: 'var(--text-secondary)',
              lineHeight: 1.6,
            }}>
              "{attempt.reasoning}"
            </div>
          </div>

          {/* Financials — ruled row */}
          <div style={{ display: 'flex', borderBottom: 'var(--rule)', paddingBottom: 12, marginBottom: 12 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 9, fontWeight: 600, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 3 }}>Amount</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--text)', fontSize: 16 }}>₹{Number(tx?.amount || 0).toLocaleString('en-IN')}</div>
            </div>
            <div style={{ flex: 1, borderLeft: 'var(--rule)', paddingLeft: 12 }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 9, fontWeight: 600, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 3 }}>Recovered</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: attempt.amountRecovered > 0 ? 'var(--ink-green)' : 'var(--text-muted)', fontSize: 16 }}>
                {attempt.amountRecovered > 0 ? `₹${Number(attempt.amountRecovered).toLocaleString('en-IN')}` : '—'}
              </div>
            </div>
            <div style={{ flex: 1, borderLeft: 'var(--rule)', paddingLeft: 12 }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 9, fontWeight: 600, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 3 }}>Cost</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--text-muted)', fontSize: 16 }}>
                {attempt.interventionCost > 0 ? `₹${Number(attempt.interventionCost).toFixed(2)}` : '—'}
              </div>
            </div>
          </div>

          {/* Transaction Context — ruled section */}
          <div>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 9, fontWeight: 600, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 8 }}>
              Transaction Context
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 16px', fontSize: 12, fontFamily: 'var(--font-mono)' }}>
              {[
                ['Failure reason', tx?.failureReason?.replaceAll('_', ' ')],
                ['Retry count', tx?.retryCount],
                ['Status', tx?.status],
                ['Confidence', `${(attempt.confidence * 100).toFixed(0)}%`],
                sub && ['Plan', sub.planName],
                sub && ['Billing', sub.billingCycle],
                customer && ['Customer', customer.name],
                customer && ['Reliability', `${(customer.paymentReliabilityScore * 100).toFixed(0)}%`],
              ].filter(Boolean).map(([label, value]) => (
                <div key={label}>
                  <span style={{ color: 'var(--text-muted)' }}>{label}: </span>
                  <span style={{ color: 'var(--text)', fontWeight: 500 }}>{value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
