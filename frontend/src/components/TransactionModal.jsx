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
        background: 'rgba(0,0,0,0.4)',
        backdropFilter: 'blur(4px)',
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
          width: 560,
          maxHeight: '80vh',
          overflow: 'auto',
          borderRadius: 'var(--radius-lg)',
          boxShadow: 'var(--shadow-lg)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{
          padding: '20px 24px',
          borderBottom: '1px solid var(--border)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <div>
            <div style={{
              fontFamily: 'var(--font-body)',
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: '0.06em',
              textTransform: 'uppercase',
              color: 'var(--text-muted)',
              marginBottom: 4,
            }}>
              Case File — Transaction Detail
            </div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 24, fontWeight: 700, color: 'var(--text)' }}>
              TXN#{tx?.id}
            </div>
            {attempt.batchId && (
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
                BATCH: {attempt.batchId.slice(0, 12)}
              </div>
            )}
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'var(--surface-2)',
              border: 'none',
              borderRadius: 'var(--radius-full)',
              width: 32,
              height: 32,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--text-muted)',
              fontSize: 14,
              fontFamily: 'var(--font-body)',
            }}
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div style={{ padding: '20px 24px' }}>
          {/* Action & Outcome */}
          <div style={{ display: 'flex', gap: 16, marginBottom: 16 }}>
            <div style={{ flex: 1, background: 'var(--surface-2)', borderRadius: 'var(--radius-sm)', padding: '12px 14px' }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>Action</div>
              <div style={{ fontFamily: 'var(--font-body)', fontWeight: 600, color: 'var(--text)', fontSize: 14 }}>{attempt.actionTaken?.replaceAll('_', ' ')}</div>
            </div>
            <div style={{ flex: 1, background: 'var(--surface-2)', borderRadius: 'var(--radius-sm)', padding: '12px 14px' }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>Outcome</div>
              <div style={{
                fontFamily: 'var(--font-body)',
                fontWeight: 700,
                fontSize: 14,
                color: attempt.outcome === 'SUCCESS' ? 'var(--ink-green)' : attempt.outcome === 'FAILED' ? 'var(--ink-red)' : 'var(--ink-amber)',
              }}>
                {attempt.outcome}
              </div>
            </div>
            <div style={{ flex: 1, background: 'var(--surface-2)', borderRadius: 'var(--radius-sm)', padding: '12px 14px' }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>LLM Driven</div>
              <div style={{ fontFamily: 'var(--font-body)', fontWeight: 500, color: attempt.llmDriven ? 'var(--ink-purple)' : 'var(--text-muted)', fontSize: 14 }}>
                {attempt.llmDriven ? 'YES' : 'NO'}
              </div>
            </div>
          </div>

          {/* Signoff warning */}
          {attempt.requiresHumanSignoff && (
            <div style={{
              padding: '10px 14px',
              background: 'var(--red-bg)',
              border: '1px solid #FECACA',
              borderRadius: 'var(--radius-sm)',
              marginBottom: 16,
              fontFamily: 'var(--font-body)',
              fontSize: 13,
              color: 'var(--ink-red)',
              fontWeight: 500,
            }}>
              ⚠ Requires Human Signoff: {attempt.signoffReason || 'No reason provided'}
            </div>
          )}

          {/* Reasoning */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 6, letterSpacing: '0.06em' }}>
              Agent Reasoning
            </div>
            <div style={{
              padding: '12px 14px',
              background: 'var(--surface-2)',
              borderRadius: 'var(--radius-sm)',
              fontFamily: 'var(--font-body)',
              fontSize: 13,
              color: 'var(--text-secondary)',
              lineHeight: 1.6,
            }}>
              "{attempt.reasoning}"
            </div>
          </div>

          {/* Financials */}
          <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
            <div style={{ flex: 1, background: 'var(--surface-2)', borderRadius: 'var(--radius-sm)', padding: '12px 14px' }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>Amount</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--text)', fontSize: 18 }}>₹{Number(tx?.amount || 0).toLocaleString('en-IN')}</div>
            </div>
            <div style={{ flex: 1, background: 'var(--surface-2)', borderRadius: 'var(--radius-sm)', padding: '12px 14px' }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>Recovered</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: attempt.amountRecovered > 0 ? 'var(--ink-green)' : 'var(--text-muted)', fontSize: 18 }}>
                {attempt.amountRecovered > 0 ? `₹${Number(attempt.amountRecovered).toLocaleString('en-IN')}` : '—'}
              </div>
            </div>
            <div style={{ flex: 1, background: 'var(--surface-2)', borderRadius: 'var(--radius-sm)', padding: '12px 14px' }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>Cost</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--text-muted)', fontSize: 18 }}>
                {attempt.interventionCost > 0 ? `₹${Number(attempt.interventionCost).toFixed(2)}` : '—'}
              </div>
            </div>
          </div>

          {/* Transaction Context */}
          <div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 8, letterSpacing: '0.06em' }}>
              Transaction Context
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 16px', fontSize: 12, fontFamily: 'var(--font-body)' }}>
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
                <div key={label} style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-muted)' }}>{label}</span>
                  <span style={{ color: 'var(--text)', fontWeight: 500, fontFamily: 'var(--font-mono)', fontSize: 12 }}>{value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
